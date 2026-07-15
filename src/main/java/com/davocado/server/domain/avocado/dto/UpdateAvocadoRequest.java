package com.davocado.server.domain.avocado.dto;

import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /avocados/{id}}. Only non-null fields are updated. */
public record UpdateAvocadoRequest(String storageCondition, Integer targetStage, String status, @Size(max = 50) String nickname) {}
