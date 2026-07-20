package com.davocado.server.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared base for MockMvc + H2 integration tests.
 *
 * <p>Each test method runs inside a transaction that is rolled back afterwards, so tests don't
 * need to clean up data they create.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Signs up a fresh user with the given credentials and returns their access token. */
    protected String signupAndLogin(String email, String password) throws Exception {
        Map<String, Object> signupBody = Map.of(
                "email", email,
                "password", password);
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(signupBody)))
                .andExpect(status().isCreated());

        Map<String, Object> loginBody = Map.of(
                "email", email,
                "password", password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("access_token").asText();
    }

    protected String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
