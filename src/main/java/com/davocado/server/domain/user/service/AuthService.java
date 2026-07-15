package com.davocado.server.domain.user.service;

import com.davocado.server.domain.user.dto.LoginRequest;
import com.davocado.server.domain.user.dto.PasswordResetRequest;
import com.davocado.server.domain.user.dto.SignupRequest;
import com.davocado.server.domain.user.dto.SignupResponse;
import com.davocado.server.domain.user.dto.TokenResponse;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.global.auth.JwtTokenProvider;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles signup, login, and password reset. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.builder()
                .loginId(request.loginId())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        return SignupResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository
                .findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.updateLastLogin(Instant.now());

        String token = jwtTokenProvider.createToken(user.getId());
        return TokenResponse.of(token, jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    /**
     * Stub for {@code POST /auth/password/reset}.
     *
     * <p>TODO: wire up actual email dispatch once email infrastructure exists. For now this
     * intentionally does nothing and never reveals whether the account exists, to avoid leaking
     * account enumeration info.
     */
    public void requestPasswordReset(PasswordResetRequest request) {
        // No-op stub. Real implementation will look up the account by email and send a reset
        // link/token, without revealing whether the email is registered.
    }
}
