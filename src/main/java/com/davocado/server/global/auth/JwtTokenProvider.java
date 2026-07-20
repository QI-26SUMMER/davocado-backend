package com.davocado.server.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the application's single JWT access token.
 *
 * <p>No refresh token, no blacklist — a token is valid until it expires, at which point the
 * client must log in again.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValiditySeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    /** Issues a signed access token with the given user id as subject. */
    public String createToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenValiditySeconds);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token, returning the user id encoded as its subject.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired
     * @throws io.jsonwebtoken.JwtException if the token is otherwise invalid (bad signature,
     *     malformed, etc.)
     */
    public Long getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    /** TTL (seconds) configured for access tokens; exposed so login responses can echo it. */
    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
}
