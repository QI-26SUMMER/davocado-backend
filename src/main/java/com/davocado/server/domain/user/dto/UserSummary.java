package com.davocado.server.domain.user.dto;

import com.davocado.server.domain.user.entity.User;

/**
 * The user object embedded in the login response so the client can render Settings without a
 * follow-up call. See API spec v1.0 section 1.2.
 */
public record UserSummary(
        Long id,
        String email,
        String nickname,
        Integer preferredStage,
        boolean pushEnabled,
        Integer advanceNoticeDays) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPreferredStage(),
                user.isPushEnabled(),
                user.getAdvanceNoticeDays());
    }
}
