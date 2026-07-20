package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for {@code PATCH /users/me/settings}. Only non-null fields are updated.
 *
 * <p>Changing {@code preferredStage} is not retroactive — each scan keeps the target it was taken
 * with (API spec v1.0 section 2.2).
 */
public record UpdateSettingsRequest(
        @Min(1) @Max(5) Integer preferredStage,
        Boolean pushEnabled,
        @Min(0) @Max(3) Integer advanceNoticeDays) {}
