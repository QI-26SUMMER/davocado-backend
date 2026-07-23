package com.davocado.server.domain.scan.service;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.notification.service.NotificationService;
import com.davocado.server.domain.scan.dto.NotificationSummary;
import com.davocado.server.domain.scan.dto.ScanListItem;
import com.davocado.server.domain.scan.dto.ScanListResponse;
import com.davocado.server.domain.scan.dto.ScanNotificationResponse;
import com.davocado.server.domain.scan.dto.ScanResponse;
import com.davocado.server.domain.scan.dto.ScanStatsResponse;
import com.davocado.server.domain.scan.entity.Image;
import com.davocado.server.domain.scan.entity.Scan;
import com.davocado.server.domain.scan.infra.PredictionResult;
import com.davocado.server.domain.scan.infra.RipenessPredictor;
import com.davocado.server.domain.scan.repository.ImageRepository;
import com.davocado.server.domain.scan.repository.ScanRepository;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import com.davocado.server.global.storage.ImageStorage;
import com.davocado.server.global.storage.ImageUrlSigner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and delete operations over the caller's scans. Scan creation lives with the AI integration. */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final String STATUS_SENT = "sent";
    private static final String STATUS_SCHEDULED = "scheduled";

    private static final Set<String> SOURCES = Set.of("camera", "gallery");

    private final ScanRepository scanRepository;
    private final ImageRepository imageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ImageUrlSigner imageUrlSigner;
    private final ImageStorage imageStorage;
    private final RipenessPredictor ripenessPredictor;

    public ScanService(
            ScanRepository scanRepository,
            ImageRepository imageRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            UserRepository userRepository,
            ImageUrlSigner imageUrlSigner,
            ImageStorage imageStorage,
            RipenessPredictor ripenessPredictor) {
        this.scanRepository = scanRepository;
        this.imageRepository = imageRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.imageUrlSigner = imageUrlSigner;
        this.imageStorage = imageStorage;
        this.ripenessPredictor = ripenessPredictor;
    }

    /**
     * Runs a scan: classify the photo, persist the result, then store the original image.
     *
     * <p>The scan row is saved before the upload even though the spec lists the upload first: the
     * documented object path {@code raw/{user_id}/{scan_id}.jpg} needs an id that only exists once
     * the row is written.
     */
    @Transactional
    public ScanResponse create(Long userId, byte[] imageBytes, String source, BigDecimal tempCelsius) {
        if (!SOURCES.contains(source)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "source must be camera or gallery");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        PredictionResult prediction =
                ripenessPredictor.predict(imageBytes, user.getPreferredStage(), tempCelsius);

        // Plain date arithmetic on the AI's number, not ripeness/beta logic, so it stays in Spring.
        LocalDate estimatedPeakDate = prediction.daysToTarget() == null
                ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(Math.round(prediction.daysToTarget().doubleValue()));

        Scan scan = scanRepository.save(Scan.builder()
                .user(user)
                // Snapshot the target now so later settings changes cannot rewrite this scan's D-day.
                .targetStage(user.getPreferredStage())
                .tempCelsius(tempCelsius)
                .predictedStage(prediction.predictedStage())
                .confidence(prediction.confidence())
                .stageProbs(prediction.stageProbs())
                .daysToTarget(prediction.daysToTarget())
                .estimatedPeakDate(estimatedPeakDate)
                .modelVersion(prediction.modelVersion())
                .build());

        Image image = storeImages(user.getId(), scan, imageBytes, prediction.croppedB64(), source);

        // Step 6: auto-schedule a notification when the user has opted into push (API spec v1.0
        // section 3.1). scheduleForScan itself no-ops when there is no future peak to notify for.
        if (user.isPushEnabled()) {
            notificationService.scheduleForScan(user, scan);
        }

        return toResponse(scan, image);
    }

    /** Signs the original and cropped images (both null when GCS is off) into a detail response. */
    private ScanResponse toResponse(Scan scan, Image image) {
        String originalUrl = image == null ? null : imageUrlSigner.sign(image.getImageUrl());
        String croppedUrl = image == null ? null : imageUrlSigner.sign(image.getCroppedUrl());
        return ScanResponse.of(scan, image, originalUrl, croppedUrl);
    }

    /**
     * Uploads the original (and the crop, when the AI returned one) and records the row.
     *
     * <p>Returns null when GCS is off — {@code images.image_url} is NOT NULL, so we skip the row.
     */
    private Image storeImages(Long userId, Scan scan, byte[] imageBytes, String croppedB64, String source) {
        String imageUrl = imageStorage.upload(
                "raw/" + userId + "/" + scan.getId() + ".jpg", imageBytes, MediaType.IMAGE_JPEG_VALUE);
        if (imageUrl == null) {
            return null;
        }
        return imageRepository.save(Image.builder()
                .scan(scan)
                .imageUrl(imageUrl)
                .croppedUrl(uploadCrop(userId, scan.getId(), croppedB64))
                .source(source)
                .build());
    }

    /** Decodes and stores the AI's crop. A bad crop must not fail a scan that already classified. */
    private String uploadCrop(Long userId, Long scanId, String croppedB64) {
        if (croppedB64 == null || croppedB64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(croppedB64);
            return imageStorage.upload(
                    "cropped/" + userId + "/" + scanId + ".jpg", bytes, MediaType.IMAGE_JPEG_VALUE);
        } catch (RuntimeException ex) {
            log.warn("Failed to store cropped image for scan {}", scanId, ex);
            return null;
        }
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
                    // History shows the user's original photo, not the crop.
                    String thumbnail = image == null ? null : imageUrlSigner.sign(image.getImageUrl());
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
        return toResponse(scan, imageRepository.findByScanId(id).orElse(null));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // The image and notification rows go with it via ON DELETE CASCADE (DB spec v1.0 section 5).
        scanRepository.delete(loadOwned(userId, id));
    }

    /** Toggles the History row's bell icon. See API spec v1.0 section 3.6. */
    @Transactional
    public ScanNotificationResponse toggleNotification(Long userId, Long id, boolean enabled) {
        Scan scan = loadOwned(userId, id);
        NotificationSummary summary = enabled
                ? notificationService.scheduleForScan(scan.getUser(), scan)
                : notificationService.cancelScheduled(scan.getId());
        return ScanNotificationResponse.of(scan.getId(), summary);
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
