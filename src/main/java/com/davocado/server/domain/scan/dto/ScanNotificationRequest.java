package com.davocado.server.domain.scan.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for {@code PATCH /scans/{id}/notification}. See API spec v1.0 section 3.6. */
public record ScanNotificationRequest(@NotNull Boolean enabled) {}
