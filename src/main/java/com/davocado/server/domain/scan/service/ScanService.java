package com.davocado.server.domain.scan.service;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.scan.dto.ScanListItem;
import com.davocado.server.domain.scan.dto.ScanListResponse;
import com.davocado.server.domain.scan.dto.ScanResponse;
import com.davocado.server.domain.scan.dto.ScanStatsResponse;
import com.davocado.server.domain.scan.entity.Image;
import com.davocado.server.domain.scan.entity.Scan;
import com.davocado.server.domain.scan.repository.ImageRepository;
import com.davocado.server.domain.scan.repository.ScanRepository;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import com.davocado.server.global.storage.ImageUrlSigner;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and delete operations over the caller's scans. Scan creation lives with the AI integration. */
@Service
public class ScanService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final String STATUS_SENT = "sent";
    private static final String STATUS_SCHEDULED = "scheduled";

    private final ScanRepository scanRepository;
    private final ImageRepository imageRepository;
    private final NotificationRepository notificationRepository;
    private final ImageUrlSigner imageUrlSigner;

    public ScanService(
            ScanRepository scanRepository,
            ImageRepository imageRepository,
            NotificationRepository notificationRepository,
            ImageUrlSigner imageUrlSigner) {
        this.scanRepository = scanRepository;
        this.imageRepository = imageRepository;
        this.notificationRepository = notificationRepository;
        this.imageUrlSigner = imageUrlSigner;
    }

    @Transactional(readOnly = true)
    public ScanListResponse list(Long userId, Integer limit, Long cursor) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        Pageable pageable = Pageable.ofSize(effectiveLimit + 1);

        List<Scan> rows = cursor != null
                ? scanRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable)
                : scanRepository.findByUserIdOrderByIdDesc(userId, pageable);

        boolean hasMore = rows.size() > effectiveLimit;
        List<Scan> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        Map<Long, Notification> notifications = notificationsFor(page);
        Map<Long, Image> images = imagesFor(page);
        List<ScanListItem> items = page.stream()
                .map(scan -> {
                    Image image = images.get(scan.getId());
                    String thumbnail = image == null ? null : imageUrlSigner.sign(image.getCroppedUrl());
                    return ScanListItem.of(scan, notifications.get(scan.getId()), thumbnail);
                })
                .toList();
        return new ScanListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public ScanStatsResponse stats(Long userId) {
        return new ScanStatsResponse(
                scanRepository.countByUserId(userId),
                notificationRepository.countByUserIdAndStatus(userId, STATUS_SENT),
                notificationRepository.countByUserIdAndStatus(userId, STATUS_SCHEDULED));
    }

    @Transactional(readOnly = true)
    public ScanResponse get(Long userId, Long id) {
        Scan scan = loadOwned(userId, id);
        Image image = imageRepository.findByScanId(id).orElse(null);
        String croppedUrl = image == null ? null : imageUrlSigner.sign(image.getCroppedUrl());
        return ScanResponse.of(scan, image, croppedUrl);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // The image and notification rows go with it via ON DELETE CASCADE (DB spec v1.0 section 5).
        scanRepository.delete(loadOwned(userId, id));
    }

    /** Loads the scan in one query and rejects it if it belongs to someone else. */
    private Scan loadOwned(Long userId, Long id) {
        Scan scan = scanRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!scan.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return scan;
    }

    /** Fetches the page's notifications in one query so the list does not trigger an N+1. */
    private Map<Long, Notification> notificationsFor(List<Scan> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> scanIds = page.stream().map(Scan::getId).toList();
        return notificationRepository.findByScanIdIn(scanIds).stream()
                .collect(Collectors.toMap(n -> n.getScan().getId(), Function.identity(), (a, b) -> a));
    }

    /** Same idea for the thumbnails backing each row. */
    private Map<Long, Image> imagesFor(List<Scan> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> scanIds = page.stream().map(Scan::getId).toList();
        return imageRepository.findByScanIdIn(scanIds).stream()
                .collect(Collectors.toMap(i -> i.getScan().getId(), Function.identity(), (a, b) -> a));
    }
}
