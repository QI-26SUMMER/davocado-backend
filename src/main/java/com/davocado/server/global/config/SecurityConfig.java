package com.davocado.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security skeleton.
 *
 * <p>Stateless, CSRF disabled (token-based auth). Public endpoints are open now; everything else
 * requires authentication. The JWT authentication filter is NOT wired in yet.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Token-based API: no CSRF tokens, no server-side session.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Open endpoints: auth flow, health check, API docs.
                        .requestMatchers(
                                "/auth/**",
                                "/health",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // TODO: tighten per-endpoint rules once real APIs land.
                        .anyRequest().authenticated()
                );

        // TODO: register the JWT authentication filter here once implemented
        //       (global.auth.JwtAuthenticationFilter), e.g.:
        //       http.addFilterBefore(jwtAuthenticationFilter,
        //               UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt for password hashing at signup and verification at login. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
