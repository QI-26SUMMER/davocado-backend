package com.davocado.server.domain.user.dto;

import com.davocado.server.domain.user.entity.User;
import java.time.Instant;

/** Response body for {@code GET/PATCH /users/me}. See API spec v1.0 section 2.1. */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        Integer preferredStage,
        boolean pushEnabled,
        Integer advanceNoticeDays,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPreferredStage(),
                user.isPushEnabled(),
                user.getAdvanceNoticeDays(),
                user.getCreatedAt());
    }
}
