package com.davocado.server.domain.scan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.scan.entity.Image;
import com.davocado.server.domain.scan.entity.Scan;
import com.davocado.server.domain.scan.repository.ImageRepository;
import com.davocado.server.domain.scan.repository.ScanRepository;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the read side of {@code /scans}.
 *
 * <p>Scans are inserted through the repository rather than an endpoint: {@code POST /scans} needs
 * the AI service (predicted_stage and model_version are NOT NULL), which is not deployed yet.
 */
class ScanQueryIntegrationTest extends IntegrationTest {

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private String authHeader(String token) {
        return "Bearer " + token;
    }

    private User userOf(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Scan newScan(User user, int predictedStage, BigDecimal daysToTarget) {
        return scanRepository.save(Scan.builder()
                .user(user)
                .targetStage(3)
                .predictedStage(predictedStage)
                .confidence(new BigDecimal("0.8700"))
                .stageProbs(List.of(0.05, 0.87, 0.06, 0.01, 0.01))
                .daysToTarget(daysToTarget)
                .estimatedPeakDate(LocalDate.of(2026, 7, 23))
                .modelVersion("resnet18_v3")
                .build());
    }

    @Nested
    @DisplayName("GET /scans")
    class ListScans {

        @Test
        @DisplayName("returns the caller's scans newest first with derived display values")
        void listsNewestFirst() throws Exception {
            String token = signupAndLogin("scan_list1@example.com", "password123");
            User user = userOf("scan_list1@example.com");
            newScan(user, 2, new BigDecimal("3.4"));
            Scan newest = newScan(user, 4, new BigDecimal("-1.2"));

            mockMvc.perform(get("/scans").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(newest.getId()))
                    .andExpect(jsonPath("$.data.items[0].predicted_stage").value(4))
                    .andExpect(jsonPath("$.data.items[0].target_stage").value(3))
                    // -1.2 rounds to -1 -> overripe
                    .andExpect(jsonPath("$.data.items[0].display.dday_text").value("D+1"))
                    .andExpect(jsonPath("$.data.items[0].display.status").value("overripe"))
                    // 3.4 rounds to 3 -> still ripening
                    .andExpect(jsonPath("$.data.items[1].display.dday_text").value("D-3"))
                    .andExpect(jsonPath("$.data.items[1].display.status").value("ripening"))
                    // stage_label is intentionally absent; the client derives it.
                    .andExpect(jsonPath("$.data.items[0].display.stage_label").doesNotExist())
                    .andExpect(jsonPath("$.data.next_cursor").doesNotExist());
        }

        @Test
        @DisplayName("rounds a half day up and reports D-Day at zero")
        void roundsHalfUp() throws Exception {
            String token = signupAndLogin("scan_round@example.com", "password123");
            User user = userOf("scan_round@example.com");
            newScan(user, 3, new BigDecimal("0.4"));

            mockMvc.perform(get("/scans").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].display.dday_text").value("D-Day"))
                    .andExpect(jsonPath("$.data.items[0].display.status").value("eat_now"));
        }

        @Test
        @DisplayName("reports notification state for the bell icon")
        void reportsNotificationState() throws Exception {
            String token = signupAndLogin("scan_bell@example.com", "password123");
            User user = userOf("scan_bell@example.com");
            Scan withoutNotification = newScan(user, 2, new BigDecimal("4.0"));
            Scan withNotification = newScan(user, 2, new BigDecimal("2.0"));
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .scan(withNotification)
                    .scheduledAt(Instant.parse("2026-07-22T09:00:00Z"))
                    .payload(Map.of("title", "곧 적기예요"))
                    .build());

            mockMvc.perform(get("/scans").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].id").value(withNotification.getId()))
                    .andExpect(jsonPath("$.data.items[0].notification.status").value("scheduled"))
                    .andExpect(jsonPath("$.data.items[1].id").value(withoutNotification.getId()))
                    .andExpect(jsonPath("$.data.items[1].notification.status").value("none"));
        }

        @Test
        @DisplayName("returns only the caller's scans")
        void isolatedPerUser() throws Exception {
            String tokenA = signupAndLogin("scan_usera@example.com", "password123");
            signupAndLogin("scan_userb@example.com", "password123");
            newScan(userOf("scan_usera@example.com"), 2, new BigDecimal("3.0"));
            newScan(userOf("scan_userb@example.com"), 2, new BigDecimal("3.0"));

            mockMvc.perform(get("/scans").header("Authorization", authHeader(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1));
        }

        @Test
        @DisplayName("cursor pagination pages through all results")
        void cursorPagination() throws Exception {
            String token = signupAndLogin("scan_cursor@example.com", "password123");
            User user = userOf("scan_cursor@example.com");
            for (int i = 0; i < 5; i++) {
                newScan(user, 2, new BigDecimal("3.0"));
            }

            MvcResult firstPage = mockMvc.perform(get("/scans")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.next_cursor").isNotEmpty())
                    .andReturn();

            JsonNode json = objectMapper.readTree(firstPage.getResponse().getContentAsString());
            long cursor = json.path("data").path("next_cursor").asLong();

            mockMvc.perform(get("/scans")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3")
                            .param("cursor", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.next_cursor").doesNotExist());
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/scans")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /scans/stats")
    class Stats {

        @Test
        @DisplayName("counts scans, sent notifications, and scheduled notifications")
        void counts() throws Exception {
            String token = signupAndLogin("scan_stats@example.com", "password123");
            User user = userOf("scan_stats@example.com");
            Scan first = newScan(user, 2, new BigDecimal("3.0"));
            Scan second = newScan(user, 2, new BigDecimal("2.0"));
            newScan(user, 2, new BigDecimal("1.0"));

            notificationRepository.save(Notification.builder()
                    .user(user)
                    .scan(first)
                    .scheduledAt(Instant.parse("2026-07-22T09:00:00Z"))
                    .build());
            Notification sent = notificationRepository.save(Notification.builder()
                    .user(user)
                    .scan(second)
                    .scheduledAt(Instant.parse("2026-07-21T09:00:00Z"))
                    .build());
            sent.markSent(Instant.parse("2026-07-21T09:00:01Z"));
            notificationRepository.save(sent);

            mockMvc.perform(get("/scans/stats").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andExpect(jsonPath("$.data.notified").value(1))
                    .andExpect(jsonPath("$.data.pending").value(1));
        }

        @Test
        @DisplayName("returns zeros for a user with no scans")
        void emptyUser() throws Exception {
            String token = signupAndLogin("scan_stats_empty@example.com", "password123");

            mockMvc.perform(get("/scans/stats").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.notified").value(0))
                    .andExpect(jsonPath("$.data.pending").value(0));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/scans/stats")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /scans/{id}")
    class GetOne {

        @Test
        @DisplayName("returns the full result payload when owned by the caller")
        void getOwned() throws Exception {
            String token = signupAndLogin("scan_get@example.com", "password123");
            Scan scan = newScan(userOf("scan_get@example.com"), 2, new BigDecimal("3.0"));
            imageRepository.save(Image.builder()
                    .scan(scan)
                    .imageUrl("gs://d-avocado-images/raw/1/" + scan.getId() + ".jpg")
                    .croppedUrl("gs://d-avocado-images/cropped/1/" + scan.getId() + ".jpg")
                    .source("camera")
                    .build());

            mockMvc.perform(get("/scans/{id}", scan.getId()).header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(scan.getId()))
                    .andExpect(jsonPath("$.data.predicted_stage").value(2))
                    .andExpect(jsonPath("$.data.confidence").exists())
                    .andExpect(jsonPath("$.data.stage_probs.length()").value(5))
                    .andExpect(jsonPath("$.data.model_version").value("resnet18_v3"))
                    .andExpect(jsonPath("$.data.display.dday_text").value("D-3"))
                    .andExpect(jsonPath("$.data.image").exists());
        }

        @Test
        @DisplayName("another user's scan returns 403 FORBIDDEN")
        void anotherUsersScan() throws Exception {
            signupAndLogin("scan_get_owner@example.com", "password123");
            String otherToken = signupAndLogin("scan_get_other@example.com", "password123");
            Scan scan = newScan(userOf("scan_get_owner@example.com"), 2, new BigDecimal("3.0"));

            mockMvc.perform(get("/scans/{id}", scan.getId()).header("Authorization", authHeader(otherToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("missing id returns 404 NOT_FOUND")
        void missingId() throws Exception {
            String token = signupAndLogin("scan_get_missing@example.com", "password123");

            mockMvc.perform(get("/scans/{id}", 999_999L).header("Authorization", authHeader(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/scans/{id}", 1L)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /scans/{id}")
    class Delete {

        @Test
        @DisplayName("deletes the scan along with its image and notification")
        void deleteCascades() throws Exception {
            String token = signupAndLogin("scan_delete@example.com", "password123");
            User user = userOf("scan_delete@example.com");
            Scan scan = newScan(user, 2, new BigDecimal("3.0"));
            imageRepository.save(Image.builder()
                    .scan(scan)
                    .imageUrl("gs://d-avocado-images/raw/1/" + scan.getId() + ".jpg")
                    .source("camera")
                    .build());
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .scan(scan)
                    .scheduledAt(Instant.parse("2026-07-22T09:00:00Z"))
                    .build());

            mockMvc.perform(delete("/scans/{id}", scan.getId()).header("Authorization", authHeader(token)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/scans/{id}", scan.getId()).header("Authorization", authHeader(token)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user's scan returns 403 FORBIDDEN")
        void anotherUsersScan() throws Exception {
            signupAndLogin("scan_del_owner@example.com", "password123");
            String otherToken = signupAndLogin("scan_del_other@example.com", "password123");
            Scan scan = newScan(userOf("scan_del_owner@example.com"), 2, new BigDecimal("3.0"));

            mockMvc.perform(delete("/scans/{id}", scan.getId()).header("Authorization", authHeader(otherToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(delete("/scans/{id}", 1L)).andExpect(status().isUnauthorized());
        }
    }
}
