package com.davocado.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.support.IntegrationTest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Integration tests for {@code /auth/**} (signup, login, logout, password reset). */
class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("POST /auth/signup")
    class Signup {

        @Test
        @DisplayName("creates a user and returns its fields")
        void signupSuccess() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "signup1@example.com",
                    "password", "password123",
                    "nickname", "avocado-lover");

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.email").value("signup1@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("avocado-lover"))
                    .andExpect(jsonPath("$.data.created_at").exists());
        }

        @Test
        @DisplayName("duplicate email is rejected with 409 DUPLICATE_EMAIL")
        void duplicateEmail() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "dup_user@example.com",
                    "password", "password123");

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("blank password is rejected with 422 VALIDATION_FAILED")
        void blankPassword() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "blank_pw_user@example.com",
                    "password", "");

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("too-short password is rejected with 422 VALIDATION_FAILED")
        void tooShortPassword() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "short_pw_user@example.com",
                    "password", "abc123");

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("malformed email is rejected with 422 VALIDATION_FAILED")
        void malformedEmail() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "not-an-email",
                    "password", "password123");

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("stores a BCrypt hash, never the raw password")
        void passwordIsHashed() throws Exception {
            String rawPassword = "password123";
            Map<String, Object> body = Map.of(
                    "email", "hash_check_user@example.com",
                    "password", rawPassword);

            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated());

            Optional<User> saved = userRepository.findByEmail("hash_check_user@example.com");
            assertThat(saved).isPresent();
            String storedHash = saved.get().getPasswordHash();
            assertThat(storedHash).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, storedHash)).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("valid credentials return an access token")
        void loginSuccess() throws Exception {
            Map<String, Object> signupBody = Map.of(
                    "email", "login_user1@example.com",
                    "password", "password123");
            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(signupBody)))
                    .andExpect(status().isCreated());

            Map<String, Object> loginBody = Map.of(
                    "email", "login_user1@example.com",
                    "password", "password123");
            mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content(asJson(loginBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.data.expires_in").value(1209600))
                    // Settings ride along so the client can render Settings without a second call.
                    .andExpect(jsonPath("$.data.user.email").value("login_user1@example.com"))
                    .andExpect(jsonPath("$.data.user.preferred_stage").value(3))
                    .andExpect(jsonPath("$.data.user.push_enabled").value(true))
                    .andExpect(jsonPath("$.data.user.advance_notice_days").value(1));
        }

        @Test
        @DisplayName("wrong password returns 401 INVALID_CREDENTIALS")
        void wrongPassword() throws Exception {
            Map<String, Object> signupBody = Map.of(
                    "email", "login_user2@example.com",
                    "password", "password123");
            mockMvc.perform(post("/auth/signup")
                            .contentType("application/json")
                            .content(asJson(signupBody)))
                    .andExpect(status().isCreated());

            Map<String, Object> loginBody = Map.of(
                    "email", "login_user2@example.com",
                    "password", "wrongpassword");
            mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content(asJson(loginBody)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("unknown email returns 401 INVALID_CREDENTIALS")
        void unknownUser() throws Exception {
            Map<String, Object> loginBody = Map.of(
                    "email", "no_such_user@example.com",
                    "password", "password123");
            mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content(asJson(loginBody)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    @DisplayName("POST /auth/logout returns 204 and clears the push token")
    void logout() throws Exception {
        String token = signupAndLogin("logout_user@example.com", "password123");

        Map<String, Object> pushBody = Map.of("push_token", "fcm-token-abc");
        mockMvc.perform(put("/users/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(pushBody)))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail("logout_user@example.com").orElseThrow().getPushToken())
                .isEqualTo("fcm-token-abc");

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("logout_user@example.com").orElseThrow().getPushToken())
                .isNull();
    }

    @Test
    @DisplayName("POST /auth/logout without a token returns 401 UNAUTHORIZED")
    void logoutWithoutToken() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/password/reset returns 202")
    void passwordReset() throws Exception {
        Map<String, Object> body = Map.of("email", "someone@example.com");
        mockMvc.perform(post("/auth/password/reset")
                        .contentType("application/json")
                        .content(asJson(body)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("GET /users/me without a token returns 401 UNAUTHORIZED")
    void getMeWithoutToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/me with a garbage bearer token returns 401 UNAUTHORIZED")
    void getMeWithGarbageToken() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
