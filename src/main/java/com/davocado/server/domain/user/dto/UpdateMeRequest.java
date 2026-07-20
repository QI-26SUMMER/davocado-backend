package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /users/me}. Only non-null fields are updated. */
public record UpdateMeRequest(@Size(max = 50) String nickname) {}
