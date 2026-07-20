package com.davocado.server.domain.user.dto;

import com.davocado.server.domain.user.entity.User;
import java.time.Instant;

/** Response body for {@code POST /auth/signup}. */
public record SignupResponse(Long id, String email, String nickname, Instant createdAt) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname(), user.getCreatedAt());
    }
}
