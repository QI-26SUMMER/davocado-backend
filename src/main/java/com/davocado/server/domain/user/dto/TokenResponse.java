package com.davocado.server.domain.user.dto;

/** Response body for {@code POST /auth/login}. Carries the caller's settings alongside the token. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    public static TokenResponse of(String accessToken, long expiresIn, UserSummary user) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, user);
    }
}
