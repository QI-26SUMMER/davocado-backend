package com.davocado.server.domain.avocado.dto;

import com.davocado.server.domain.avocado.entity.Avocado;
import java.time.Instant;

/** Response body for {@code POST /avocados}, {@code GET/PATCH /avocados/{id}}. */
public record AvocadoResponse(
        Long id,
        String nickname,
        String storageCondition,
        String purpose,
        Integer targetStage,
        Integer currentStage,
        String status,
        LatestPredictionSummary latest,
        Instant lastPredictedAt,
        Instant createdAt) {

    public static AvocadoResponse from(Avocado avocado) {
        return new AvocadoResponse(
                avocado.getId(),
                avocado.getNickname(),
                avocado.getStorageCondition(),
                avocado.getPurpose(),
                avocado.getTargetStage(),
                avocado.getCurrentStage(),
                avocado.getStatus(),
                // TODO(step 4): populate from latest prediction once predictions exist
                null,
                avocado.getLastPredictedAt(),
                avocado.getCreatedAt());
    }
}
