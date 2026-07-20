package com.davocado.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Integration tests for {@code /users/me}. */
class UserProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /users/me with a valid token returns the caller's profile")
    void getMeSuccess() throws Exception {
        String token = signupAndLogin("profile_user1@example.com", "password123");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile_user1@example.com"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.created_at").exists())
                .andExpect(jsonPath("$.data.preferred_stage").value(3))
                .andExpect(jsonPath("$.data.push_enabled").value(true))
                .andExpect(jsonPath("$.data.advance_notice_days").value(1));
    }

    @Test
    @DisplayName("PATCH /users/me updates nickname, visible on a follow-up GET")
    void updateMeSuccess() throws Exception {
        String token = signupAndLogin("profile_user2@example.com", "password123");

        Map<String, Object> update = Map.of("nickname", "new-nickname");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("new-nickname"));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("new-nickname"));
    }

    @Test
    @DisplayName("PATCH /users/me/settings applies a partial update, visible on a follow-up GET")
    void updateSettings() throws Exception {
        String token = signupAndLogin("settings_user1@example.com", "password123");

        Map<String, Object> update = Map.of("preferred_stage", 5, "advance_notice_days", 0);

        mockMvc.perform(patch("/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferred_stage").value(5))
                .andExpect(jsonPath("$.data.advance_notice_days").value(0))
                // Untouched field keeps its default.
                .andExpect(jsonPath("$.data.push_enabled").value(true));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferred_stage").value(5))
                .andExpect(jsonPath("$.data.advance_notice_days").value(0));
    }

    @Test
    @DisplayName("PATCH /users/me/settings rejects out-of-range values with 422 VALIDATION_FAILED")
    void updateSettingsOutOfRange() throws Exception {
        String token = signupAndLogin("settings_user2@example.com", "password123");

        mockMvc.perform(patch("/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("preferred_stage", 6))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("advance_notice_days", 4))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /users/me/push-token registers the token and is idempotent")
    void registerPushToken() throws Exception {
        String token = signupAndLogin("push_user1@example.com", "password123");

        mockMvc.perform(put("/users/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("push_token", "token-v1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.push_token_registered").value(true));

        // Re-registering overwrites rather than failing.
        mockMvc.perform(put("/users/me/push-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("push_token", "token-v2"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("push_user1@example.com").orElseThrow().getPushToken())
                .isEqualTo("token-v2");
    }

    @Test
    @DisplayName("settings and push-token endpoints require a token")
    void requireAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me/settings")
                        .contentType("application/json")
                        .content(asJson(Map.of("preferred_stage", 4))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/users/me/push-token")
                        .contentType("application/json")
                        .content(asJson(Map.of("push_token", "x"))))
                .andExpect(status().isUnauthorized());
    }
}
