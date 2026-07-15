package com.davocado.server.domain.avocado.dto;

import java.time.LocalDate;

/** Summary of an avocado's latest prediction, embedded in avocado responses. */
public record LatestPredictionSummary(Integer daysToTarget, LocalDate estimatedPeakDate, String ddayText) {}
