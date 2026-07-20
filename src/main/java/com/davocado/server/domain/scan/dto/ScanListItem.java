package com.davocado.server.domain.scan.dto;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.scan.entity.Scan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A single row in the {@code GET /scans} History list. See API spec v1.0 section 3.2. */
public record ScanListItem(
        Long id,
        Integer predictedStage,
        Integer targetStage,
        BigDecimal daysToTarget,
        LocalDate estimatedPeakDate,
        Instant createdAt,
        ScanDisplay display,
        NotificationSummary notification,
        String thumbnailUrl) {

    /** {@code thumbnailUrl} is an already-signed URL, or null when there is no crop yet. */
    public static ScanListItem of(Scan scan, Notification notification, String thumbnailUrl) {
        return new ScanListItem(
                scan.getId(),
                scan.getPredictedStage(),
                scan.getTargetStage(),
                scan.getDaysToTarget(),
                scan.getEstimatedPeakDate(),
                scan.getCreatedAt(),
                ScanDisplay.from(scan.getDaysToTarget()),
                NotificationSummary.from(notification),
                thumbnailUrl);
    }
}
