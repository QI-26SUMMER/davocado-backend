package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /auth/login}. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
