package com.davocado.server.domain.scan.service;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.scan.dto.ScanListItem;
import com.davocado.server.domain.scan.dto.ScanListResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and delete operations over the caller's scans. Scan creation lives with the AI integration. */
@Service
public class ScanService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final String STATUS_SENT = "sent";
    private static final String STATUS_SCHEDULED = "scheduled";

    private static final Set<String> SOURCES = Set.of("camera", "gallery");

    private final ScanRepository scanRepository;
    private final ImageRepository imageRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ImageUrlSigner imageUrlSigner;
    private final ImageStorage imageStorage;
    private final RipenessPredictor ripenessPredictor;

    public ScanService(
            ScanRepository scanRepository,
            ImageRepository imageRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ImageUrlSigner imageUrlSigner,
            ImageStorage imageStorage,
            RipenessPredictor ripenessPredictor) {
        this.scanRepository = scanRepository;
        this.imageRepository = imageRepository;
        this.notificationRepository = notificationRepository;
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

        Image image = storeOriginal(user.getId(), scan, imageBytes, source);
        String croppedUrl = image == null ? null : imageUrlSigner.sign(image.getCroppedUrl());
        return ScanResponse.of(scan, image, croppedUrl);
    }

    /** Returns null when GCS is off — {@code images.image_url} is NOT NULL, so we skip the row. */
    private Image storeOriginal(Long userId, Scan scan, byte[] imageBytes, String source) {
        String objectName = "raw/" + userId + "/" + scan.getId() + ".jpg";
        String imageUrl = imageStorage.upload(objectName, imageBytes, MediaType.IMAGE_JPEG_VALUE);
        if (imageUrl == null) {
            return null;
        }
        return imageRepository.save(Image.builder()
                .scan(scan)
                .imageUrl(imageUrl)
                .source(source)
                .build());
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
