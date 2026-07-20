package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /auth/password/reset}. */
public record PasswordResetRequest(@NotBlank @Email String email) {}
