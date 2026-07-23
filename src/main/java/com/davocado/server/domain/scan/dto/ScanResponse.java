package com.davocado.server.domain.scan.dto;

import com.davocado.server.domain.scan.entity.Image;
import com.davocado.server.domain.scan.entity.Scan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response body for {@code GET /scans/{id}} — enough to rebuild the Result screen. */
public record ScanResponse(
        Long id,
        Integer predictedStage,
        BigDecimal confidence,
        List<Double> stageProbs,
        Integer targetStage,
        BigDecimal tempCelsius,
        BigDecimal daysToTarget,
        LocalDate estimatedPeakDate,
        String modelVersion,
        ScanImageSummary image,
        Instant createdAt,
        ScanDisplay display) {

    /**
     * The signed URLs are already produced by the caller. The {@code image} block is present only
     * when the scan actually has an image row.
     */
    public static ScanResponse of(Scan scan, Image image, String signedOriginalUrl, String signedCroppedUrl) {
        return new ScanResponse(
                scan.getId(),
                scan.getPredictedStage(),
                scan.getConfidence(),
                scan.getStageProbs(),
                scan.getTargetStage(),
                scan.getTempCelsius(),
                scan.getDaysToTarget(),
                scan.getEstimatedPeakDate(),
                scan.getModelVersion(),
                image == null ? null : new ScanImageSummary(signedOriginalUrl, signedCroppedUrl),
                scan.getCreatedAt(),
                ScanDisplay.from(scan.getDaysToTarget()));
    }
}
