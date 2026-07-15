package com.davocado.server.domain.user.dto;

import com.davocado.server.domain.user.entity.User;
import java.time.Instant;

/** Response body for {@code GET/PATCH /users/me}. */
public record UserResponse(
        Long id, String loginId, String email, String nickname, Instant lastLoginAt, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getEmail(),
                user.getNickname(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
