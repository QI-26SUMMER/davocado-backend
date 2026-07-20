package com.davocado.server.domain.scan.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the AI service returned for one image. Stored verbatim — Spring never computes any of it.
 *
 * <p>{@code daysToTarget} and {@code estimatedPeakDate} are nullable: the service does not return
 * them yet (the days-until-target formula is still being decided on the ML side), and the columns
 * are nullable to match.
 */
public record PredictionResult(
        Integer predictedStage,
        BigDecimal confidence,
        List<Double> stageProbs,
        String modelVersion,
        BigDecimal daysToTarget,
        LocalDate estimatedPeakDate) {}
