package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PUT /users/me/push-token}. See API spec v1.0 section 2.3. */
public record PushTokenRequest(@NotBlank @Size(max = 255) String pushToken) {}
