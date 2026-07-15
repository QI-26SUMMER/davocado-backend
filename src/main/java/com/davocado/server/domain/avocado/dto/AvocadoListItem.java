package com.davocado.server.domain.avocado.dto;

import com.davocado.server.domain.avocado.entity.Avocado;
import java.time.Instant;

/** A single item in the {@code GET /avocados} list response. */
public record AvocadoListItem(
        Long id,
        String nickname,
        String storageCondition,
        Integer targetStage,
        Integer currentStage,
        String status,
        LatestPredictionSummary latest,
        Instant lastPredictedAt) {

    public static AvocadoListItem from(Avocado avocado) {
        return new AvocadoListItem(
                avocado.getId(),
                avocado.getNickname(),
                avocado.getStorageCondition(),
                avocado.getTargetStage(),
                avocado.getCurrentStage(),
                avocado.getStatus(),
                // TODO(step 4): populate from latest prediction once predictions exist
                null,
                avocado.getLastPredictedAt());
    }
}
