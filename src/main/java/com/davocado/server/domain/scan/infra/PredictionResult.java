package com.davocado.server.domain.scan.infra;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the AI service returned for one image. Stored verbatim — Spring never computes any of it.
 *
 * <p>{@code daysToTarget} is nullable: the {@code days_to_target} column is nullable to match.
 * {@code estimatedPeakDate} is not part of this result — the AI service does not return it, so
 * Spring derives it from {@code daysToTarget} with plain date arithmetic (see {@code ScanService}).
 *
 * <p>{@code croppedB64} is the base64 JPEG of the background-removed crop (method A), present only
 * when the AI service has cropping enabled; null otherwise. Spring stores it and populates
 * {@code images.cropped_url}.
 */
public record PredictionResult(
        Integer predictedStage,
        BigDecimal confidence,
        List<Double> stageProbs,
        String modelVersion,
        BigDecimal daysToTarget,
        String croppedB64) {}
