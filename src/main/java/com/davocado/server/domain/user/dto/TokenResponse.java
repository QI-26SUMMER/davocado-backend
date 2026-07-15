package com.davocado.server.domain.user.dto;

/** Response body for {@code POST /auth/login}. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
