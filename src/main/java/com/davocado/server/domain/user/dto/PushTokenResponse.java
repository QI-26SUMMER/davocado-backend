package com.davocado.server.domain.user.dto;

/** Response body for {@code PUT /users/me/push-token}. */
public record PushTokenResponse(boolean pushTokenRegistered) {

    public static PushTokenResponse registered() {
        return new PushTokenResponse(true);
    }
}
