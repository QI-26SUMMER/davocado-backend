package com.davocado.server.domain.user.dto;

import com.davocado.server.domain.user.entity.User;

/** Response body for {@code PATCH /users/me/settings}. See API spec v1.0 section 2.2. */
public record SettingsResponse(Integer preferredStage, boolean pushEnabled, Integer advanceNoticeDays) {

    public static SettingsResponse from(User user) {
        return new SettingsResponse(user.getPreferredStage(), user.isPushEnabled(), user.getAdvanceNoticeDays());
    }
}
