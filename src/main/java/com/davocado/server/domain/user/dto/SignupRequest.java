package com.davocado.server.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/signup}. */
public record SignupRequest(
        @NotBlank @Size(min = 3, max = 50) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String nickname) {}
