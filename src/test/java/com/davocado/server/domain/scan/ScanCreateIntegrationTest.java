package com.davocado.server.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.scan.entity.Image;
import com.davocado.server.domain.scan.infra.PredictionResult;
import com.davocado.server.domain.scan.infra.RipenessPredictor;
import com.davocado.server.domain.scan.repository.ImageRepository;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import com.davocado.server.global.storage.ImageStorage;
import com.davocado.server.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Base64;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
        Integer lastTargetStage;
        BigDecimal lastTempCelsius;

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
        public PredictionResult predict(byte[] imageBytes, int targetStage, BigDecimal tempCelsius) {
            lastTargetStage = targetStage;
            lastTempCelsius = tempCelsius;
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

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    // GCS is unconfigured in tests, so the real ImageStorage returns null (no image row). Mocking it
    // lets the crop test make uploads "succeed" and assert the crop was stored; it returns null by
    // default, matching the GCS-off behaviour the other tests rely on.
    @MockitoBean
    private ImageStorage imageStorage;

    @BeforeEach
    void resetStub() {
        stubPredictor.next = StubPredictor.defaultResult();
        stubPredictor.failure = null;
        stubPredictor.lastTargetStage = null;
        stubPredictor.lastTempCelsius = null;
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
                null);

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.display.dday_text").value("D-3"))
                .andExpect(jsonPath("$.data.display.status").value("ripening"))
                // Spring derives this from days_to_target now — the AI no longer returns it.
                .andExpect(jsonPath("$.data.estimated_peak_date").exists());
    }

    @Test
    @DisplayName("schedules a notification when a future peak is derived and push is enabled")
    void schedulesNotificationOnCreate() throws Exception {
        String token = signupAndLogin("scan_create13@example.com", "password123");
        stubPredictor.next = new PredictionResult(
                2,
                new BigDecimal("0.8700"),
                List.of(0.05, 0.87, 0.06, 0.01, 0.01),
                "resnet18_v3",
                new BigDecimal("3.0"),
                null);

        MvcResult result = mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long scanId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Default push_enabled=true, so step 6 auto-schedules a notification (API spec v1.0 section 3.1).
        List<Notification> notifications = notificationRepository.findByScanId(scanId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getStatus()).isEqualTo("scheduled");
    }

    @Test
    @DisplayName("a float days_to_target is stored as-is and derives estimated_peak_date")
    void floatDaysToTarget() throws Exception {
        String token = signupAndLogin("scan_create10@example.com", "password123");
        stubPredictor.next = new PredictionResult(
                2,
                new BigDecimal("0.8700"),
                List.of(0.05, 0.87, 0.06, 0.01, 0.01),
                "resnet18_v3",
                new BigDecimal("4.5"),
                null);

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days_to_target").value(4.5))
                .andExpect(jsonPath("$.data.estimated_peak_date").exists())
                .andExpect(jsonPath("$.data.display.dday_text").exists());
    }

    @Test
    @DisplayName("sends the user's preferred stage and the supplied temperature to the AI service")
    void sendsTargetStageAndTemperature() throws Exception {
        String token = signupAndLogin("scan_create11@example.com", "password123");
        mockMvc.perform(patch("/users/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(asJson(Map.of("preferred_stage", 4))))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .file(part("temp_celsius", "17.0"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        assertThat(stubPredictor.lastTargetStage).isEqualTo(4);
        assertThat(stubPredictor.lastTempCelsius).isEqualByComparingTo(new BigDecimal("17.0"));
    }

    @Test
    @DisplayName("a returned cropped image is decoded and stored under cropped/, setting cropped_url")
    void storesCroppedImage() throws Exception {
        String token = signupAndLogin("scan_create12@example.com", "password123");
        byte[] cropBytes = "cropped-jpeg-bytes".getBytes();
        stubPredictor.next = new PredictionResult(
                2,
                new BigDecimal("0.8700"),
                List.of(0.05, 0.87, 0.06, 0.01, 0.01),
                "resnet18_v3",
                new BigDecimal("3.0"),
                Base64.getEncoder().encodeToString(cropBytes));
        // Echo each object path back as a gs:// URI so both the raw and the cropped upload "succeed".
        given(imageStorage.upload(anyString(), any(), anyString()))
                .willAnswer(inv -> "gs://bucket/" + inv.getArgument(0, String.class));

        MvcResult result = mockMvc.perform(multipart("/scans")
                        .file(jpeg())
                        .file(part("source", "camera"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long scanId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // The crop was decoded and uploaded under cropped/{userId}/{scanId}.jpg.
        verify(imageStorage).upload(
                argThat(name -> name.startsWith("cropped/") && name.endsWith("/" + scanId + ".jpg")),
                eq(cropBytes),
                anyString());
        // ...and the image row records the path (the response URL itself is a signed URL, which is
        // null here because the signer needs GCS too).
        Image image = imageRepository.findByScanId(scanId).orElseThrow();
        assertThat(image.getCroppedUrl()).contains("cropped/");
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.temp_celsius").value(21.5));

        mockMvc.perform(get("/scans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].temp_celsius").value(21.5));
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
