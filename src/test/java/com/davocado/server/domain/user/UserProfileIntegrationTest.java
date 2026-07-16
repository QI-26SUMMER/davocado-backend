package com.davocado.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.user.entity.User;
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
        String token = signupAndLogin("profile_user1", "password123");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.login_id").value("profile_user1"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.created_at").exists());
    }

    @Test
    @DisplayName("PATCH /users/me updates nickname and push_token, visible on a follow-up GET")
    void updateMeSuccess() throws Exception {
        String token = signupAndLogin("profile_user2", "password123");

        Map<String, Object> update = Map.of(
                "nickname", "new-nickname",
                "push_token", "push-token-abc");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("new-nickname"));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("new-nickname"));

        // push_token isn't part of UserResponse, so verify it was persisted via the repository.
        User user = userRepository.findByLoginId("profile_user2").orElseThrow();
        assertThat(user.getPushToken()).isEqualTo("push-token-abc");
    }
}
