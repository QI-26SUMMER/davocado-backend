package com.davocado.server.domain.scan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.scan.infra.PredictionResult;
import com.davocado.server.domain.scan.infra.RipenessPredictor;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import com.davocado.server.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for {@code POST /scans}.
 *
 * <p>The AI service is stubbed: it is not deployed yet, and even once it is, an integration test
 * must not depend on a network call. GCS is unconfigured in tests, so no image row is written.
 */
@Import(ScanCreateIntegrationTest.StubPredictorConfig.class)
class ScanCreateIntegrationTest extends IntegrationTest {

    /** Swappable stub so each test can dictate what the AI "returns". */
    static class StubPredictor implements RipenessPredictor {
        PredictionResult next = defaultResult();
        RuntimeException failure;

        static PredictionResult defaultResult() {
            return new PredictionResult(
                    2,
                    new BigDecimal("0.8700"),
                    List.of(0.05, 0.87, 0.06, 0.01, 0.01),
                    "P1_general_resnet18_paper_aug_oversample",
                    null,
                    null);
        }

        @Override
        public PredictionResult predict(byte[] imageBytes) {
            if (failure != null) {
                throw failure;
            }
            return next;
        }
    }

    @TestConfiguration
    static class StubPredictorConfig {
        @Bean
        @Primary
        StubPredictor stubPredictor() {
            return new StubPredictor();
        }
    }

    @Autowired
    private StubPredictor stubPredictor;

    @BeforeEach
    void resetStub() {
        stubPredictor.next = StubPredictor.defaultResult();
        stubPredictor.failure = null;
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "avocado.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());
    }

    private MockMultipartFile part(String name, String value) {
        return new MockMultipartFile(name, null, "text/plain", value.getBytes());
    }

    @Test
    @DisplayName("stores the prediction and returns the result payload")
    void createsScan() throws Exception {
        String token = signupAndLogin("scan_create1@example.com", "password123");

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.predicted_stage").value(2))
                // Default preferred_stage is 3, snapshotted onto the scan.
                .andExpect(jsonPath("$.data.target_stage").value(3))
                .andExpect(jsonPath("$.data.stage_probs.length()").value(5))
                .andExpect(jsonPath("$.data.model_version").value("P1_general_resnet18_paper_aug_oversample"))
                .andExpect(jsonPath("$.data.created_at").exists());
    }

    @Test
    @DisplayName("without days_to_target the scan stores null and omits display")
    void withoutDaysToTarget() throws Exception {
        String token = signupAndLogin("scan_create2@example.com", "password123");

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "gallery"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days_to_target").doesNotExist())
                .andExpect(jsonPath("$.data.estimated_peak_date").doesNotExist())
                .andExpect(jsonPath("$.data.display").doesNotExist());
    }

    @Test
    @DisplayName("with days_to_target the display block is derived")
    void withDaysToTarget() throws Exception {
        String token = signupAndLogin("scan_create3@example.com", "password123");
        stubPredictor.next = new PredictionResult(
                2,
                new BigDecimal("0.8700"),
                List.of(0.05, 0.87, 0.06, 0.01, 0.01),
                "resnet18_v3",
                new BigDecimal("3.4"),
                LocalDate.of(2026, 7, 24));

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.display.dday_text").value("D-3"))
                .andExpect(jsonPath("$.data.display.status").value("ripening"))
                .andExpect(jsonPath("$.data.estimated_peak_date").value("2026-07-24"));
    }

    @Test
    @DisplayName("target_stage is snapshotted, so changing the setting does not rewrite past scans")
    void targetStageIsSnapshotted() throws Exception {
        String token = signupAndLogin("scan_create4@example.com", "password123");

        MvcResult first = mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.target_stage").value(3))
                .andReturn();
        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        long firstId = firstJson.path("data").path("id").asLong();

        mockMvc.perform(patch("/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("preferred_stage", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.target_stage").value(5));

        // The earlier scan keeps the target it was taken with.
        mockMvc.perform(get("/scans/{id}", firstId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target_stage").value(3));
    }

    @Test
    @DisplayName("temp_celsius is optional and echoed back when supplied")
    void temperatureIsOptional() throws Exception {
        String token = signupAndLogin("scan_create5@example.com", "password123");

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .file(part("temp_celsius", "21.5"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/scans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("invalid source returns 422 VALIDATION_FAILED")
    void invalidSource() throws Exception {
        String token = signupAndLogin("scan_create6@example.com", "password123");

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "scanner"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("an empty image returns 422 VALIDATION_FAILED")
    void emptyImage() throws Exception {
        String token = signupAndLogin("scan_create7@example.com", "password123");

        mockMvc.perform(multipart("/scans")
                        .file(new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]))
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("an unavailable AI service surfaces as 502 INFERENCE_SERVICE_UNAVAILABLE")
    void inferenceUnavailable() throws Exception {
        String token = signupAndLogin("scan_create8@example.com", "password123");
        stubPredictor.failure = new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("INFERENCE_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a rejected image surfaces as 422 NO_AVOCADO_DETECTED")
    void noAvocadoDetected() throws Exception {
        String token = signupAndLogin("scan_create9@example.com", "password123");
        stubPredictor.failure = new BusinessException(ErrorCode.NO_AVOCADO_DETECTED);

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NO_AVOCADO_DETECTED"));
    }

    @Test
    @DisplayName("without a token returns 401 UNAUTHORIZED")
    void withoutToken() throws Exception {
        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera")))
                .andExpect(status().isUnauthorized());
    }
}
