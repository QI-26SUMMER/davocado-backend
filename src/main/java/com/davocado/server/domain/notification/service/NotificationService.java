package com.davocado.server.domain.notification.service;

import com.davocado.server.domain.notification.dto.NotificationListItem;
import com.davocado.server.domain.notification.dto.NotificationListResponse;
import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.infra.PushSender;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.scan.dto.NotificationSummary;
import com.davocado.server.domain.scan.entity.Scan;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedules, cancels, lists, and dispatches per-scan ripeness notifications. See API spec v1.0
 * sections 3.6 and 4.
 */
@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final String STATUS_SCHEDULED = "scheduled";
    private static final String STATUS_SENT = "sent";

    // Fires at 09:00 UTC, matching the spec's examples (e.g. 2026-07-17T09:00:00Z). A future
    // decision may make this configurable per user; for now it is a fixed constant.
    private static final int NOTIFY_HOUR_UTC = 9;

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;

    public NotificationService(NotificationRepository notificationRepository, PushSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.pushSender = pushSender;
    }

    /**
     * Schedules a notification for the scan's estimated peak, unless one already exists or the
     * peak cannot be notified for (see below). Does NOT check {@code push_enabled} — the caller
     * decides whether that applies (auto-schedule on create checks it; the manual bell toggle does
     * not, per API spec v1.0 section 3.6).
     */
    @Transactional
    public NotificationSummary scheduleForScan(User user, Scan scan) {
        List<Notification> existing = notificationRepository.findByScanId(scan.getId());
        if (!existing.isEmpty()) {
            return NotificationSummary.from(existing.get(0));
        }

        LocalDate estimatedPeakDate = scan.getEstimatedPeakDate();
        if (estimatedPeakDate == null) {
            return NotificationSummary.none();
        }

        Instant scheduledAt = estimatedPeakDate
                .minusDays(user.getAdvanceNoticeDays())
                .atTime(NOTIFY_HOUR_UTC, 0)
                .toInstant(ZoneOffset.UTC);
        if (!scheduledAt.isAfter(Instant.now())) {
            // The peak already passed (or would fire in the past) - only future peaks get a
            // notification, consistent with the 3.6 toggle rule (overripe scans are ignored).
            return NotificationSummary.none();
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .user(user)
                .scan(scan)
                .scheduledAt(scheduledAt)
                .payload(buildPayload(user.getAdvanceNoticeDays()))
                .build());
        return NotificationSummary.from(saved);
    }

    /** Deletes the scan's {@code scheduled} notification, if any; a {@code sent} row is kept. */
    @Transactional
    public NotificationSummary cancelScheduled(Long scanId) {
        Notification remaining = null;
        for (Notification notification : notificationRepository.findByScanId(scanId)) {
            if (STATUS_SCHEDULED.equals(notification.getStatus())) {
                notificationRepository.delete(notification);
            } else {
                remaining = notification;
            }
        }
        return NotificationSummary.from(remaining);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, String status, Integer limit, Long cursor) {
        String normalizedStatus = normalizeStatus(status);
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        Pageable pageable = Pageable.ofSize(effectiveLimit + 1);

        List<Notification> rows = normalizedStatus != null
                ? (cursor != null
                        ? notificationRepository.findByUserIdAndStatusAndIdLessThanOrderByIdDesc(
                                userId, normalizedStatus, cursor, pageable)
                        : notificationRepository.findByUserIdAndStatusOrderByIdDesc(userId, normalizedStatus, pageable))
                : (cursor != null
                        ? notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable)
                        : notificationRepository.findByUserIdOrderByIdDesc(userId, pageable));

        boolean hasMore = rows.size() > effectiveLimit;
        List<Notification> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        List<NotificationListItem> items = page.stream().map(NotificationListItem::of).toList();
        return new NotificationListResponse(items, nextCursor);
    }

    /** Null/blank means no filter; anything other than the two known statuses is rejected. */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (!STATUS_SCHEDULED.equals(status) && !STATUS_SENT.equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status must be scheduled or sent");
        }
        return status;
    }

    /**
     * Scheduler entry point: sends every due notification whose user still wants and can receive
     * it, and skips (leaving it {@code scheduled}) the rest so a later run can retry them.
     */
    @Transactional
    public void dispatchDue() {
        List<Notification> due = notificationRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                STATUS_SCHEDULED, Instant.now(), Pageable.ofSize(100));
        for (Notification notification : due) {
            User user = notification.getUser();
            if (user.isPushEnabled() && user.getPushToken() != null && !user.getPushToken().isBlank()) {
                pushSender.send(user.getPushToken(), notification.getPayload());
                notification.markSent(Instant.now());
                notificationRepository.save(notification);
            }
        }
    }

    /** English push copy; see API spec v1.0 section 4.1. */
    private Map<String, Object> buildPayload(int advanceNoticeDays) {
        String body = switch (advanceNoticeDays) {
            case 0 -> "It reaches your target ripeness today!";
            case 1 -> "It reaches your target ripeness tomorrow!";
            default -> "It reaches your target ripeness in " + advanceNoticeDays + " days.";
        };
        return Map.of("title", "Your avocado is almost ready 🥑", "body", body);
    }
}
