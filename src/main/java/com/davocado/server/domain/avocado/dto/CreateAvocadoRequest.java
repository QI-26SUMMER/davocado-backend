package com.davocado.server.domain.avocado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /avocados}.
 *
 * <p>{@code storageCondition} must be {@code fridge} or {@code room}. {@code purpose} (if
 * present) must be one of {@code eat_now}/{@code in_2days}/{@code weekend} and is mapped to
 * {@code targetStage} by the service unless {@code targetStage} is given directly.
 */
public record CreateAvocadoRequest(
        @Size(max = 50) String nickname,
        @NotBlank String storageCondition,
        String purpose,
        Integer targetStage) {}
