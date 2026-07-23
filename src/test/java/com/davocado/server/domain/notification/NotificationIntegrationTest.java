package com.davocado.server.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.domain.notification.entity.Notification;
import com.davocado.server.domain.notification.infra.PushSender;
import com.davocado.server.domain.notification.repository.NotificationRepository;
import com.davocado.server.domain.notification.service.NotificationService;
import com.davocado.server.domain.scan.entity.Scan;
import com.davocado.server.domain.scan.repository.ScanRepository;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the notification feature: {@code GET /notifications}, the
 * {@code PATCH /scans/{id}/notification} bell toggle, and {@link NotificationService#dispatchDue()}.
 */
class NotificationIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private UserRepository userRepository;

    // The scheduler's actual delivery is out of scope here; only dispatchDue's decision to send
    // (or skip) is under test.
    @MockitoBean
    private PushSender pushSender;

    private String authHeader(String token) {
        return "Bearer " + token;
    }

    private User userOf(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Scan newScan(User user, LocalDate estimatedPeakDate) {
        return scanRepository.save(Scan.builder()
                .user(user)
                .targetStage(user.getPreferredStage())
                .predictedStage(2)
                .confidence(new BigDecimal("0.8700"))
                .stageProbs(List.of(0.05, 0.87, 0.06, 0.01, 0.01))
                .daysToTarget(new BigDecimal("3.0"))
                .estimatedPeakDate(estimatedPeakDate)
                .modelVersion("resnet18_v3")
                .build());
    }

    private Notification newNotification(User user, Scan scan, Instant scheduledAt) {
        return notificationRepository.save(Notification.builder()
                .user(user)
                .scan(scan)
                .scheduledAt(scheduledAt)
                .payload(Map.of("title", "Your avocado is almost ready", "body", "It reaches your target ripeness tomorrow!"))
                .build());
    }

    @Nested
    @DisplayName("GET /notifications")
    class ListNotifications {

        @Test
        @DisplayName("returns the caller's notifications newest first")
        void listsNewestFirst() throws Exception {
            String token = signupAndLogin("notif_list1@example.com", "password123");
            User user = userOf("notif_list1@example.com");
            Scan scanA = newScan(user, LocalDate.of(2026, 7, 20));
            Scan scanB = newScan(user, LocalDate.of(2026, 7, 25));
            newNotification(user, scanA, Instant.parse("2026-07-19T09:00:00Z"));
            Notification newest = newNotification(user, scanB, Instant.parse("2026-07-24T09:00:00Z"));

            mockMvc.perform(get("/notifications").header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(newest.getId()))
                    .andExpect(jsonPath("$.data.items[0].scan_id").value(scanB.getId()))
                    .andExpect(jsonPath("$.data.items[0].scheduled_at").exists())
                    .andExpect(jsonPath("$.data.items[0].sent_at").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].status").value("scheduled"))
                    .andExpect(jsonPath("$.data.items[0].payload.title").exists());
        }

        @Test
        @DisplayName("status filter returns only matching notifications")
        void filtersByStatus() throws Exception {
            String token = signupAndLogin("notif_filter@example.com", "password123");
            User user = userOf("notif_filter@example.com");
            Scan scanA = newScan(user, LocalDate.of(2026, 7, 20));
            Scan scanB = newScan(user, LocalDate.of(2026, 7, 25));
            Notification scheduled = newNotification(user, scanA, Instant.parse("2026-07-24T09:00:00Z"));
            Notification sent = newNotification(user, scanB, Instant.parse("2026-07-20T09:00:00Z"));
            sent.markSent(Instant.parse("2026-07-20T09:00:01Z"));
            notificationRepository.save(sent);

            mockMvc.perform(get("/notifications")
                            .header("Authorization", authHeader(token))
                            .param("status", "sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(sent.getId()));

            mockMvc.perform(get("/notifications")
                            .header("Authorization", authHeader(token))
                            .param("status", "scheduled"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(scheduled.getId()));
        }

        @Test
        @DisplayName("an invalid status returns 422 VALIDATION_FAILED")
        void invalidStatus() throws Exception {
            String token = signupAndLogin("notif_badstatus@example.com", "password123");

            mockMvc.perform(get("/notifications")
                            .header("Authorization", authHeader(token))
                            .param("status", "foo"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("cursor pagination pages through all results")
        void cursorPagination() throws Exception {
            String token = signupAndLogin("notif_cursor@example.com", "password123");
            User user = userOf("notif_cursor@example.com");
            for (int i = 0; i < 5; i++) {
                Scan scan = newScan(user, LocalDate.of(2026, 7, 20).plusDays(i));
                newNotification(user, scan, Instant.parse("2026-07-19T09:00:00Z").plusSeconds(i));
            }

            MvcResult firstPage = mockMvc.perform(get("/notifications")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.next_cursor").isNotEmpty())
                    .andReturn();

            JsonNode json = objectMapper.readTree(firstPage.getResponse().getContentAsString());
            long cursor = json.path("data").path("next_cursor").asLong();

            mockMvc.perform(get("/notifications")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3")
                            .param("cursor", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.next_cursor").doesNotExist());
        }

        @Test
        @DisplayName("returns only the caller's notifications")
        void isolatedPerUser() throws Exception {
            String tokenA = signupAndLogin("notif_usera@example.com", "password123");
            signupAndLogin("notif_userb@example.com", "password123");
            User userA = userOf("notif_usera@example.com");
            User userB = userOf("notif_userb@example.com");
            newNotification(userA, newScan(userA, LocalDate.of(2026, 7, 25)), Instant.parse("2026-07-24T09:00:00Z"));
            newNotification(userB, newScan(userB, LocalDate.of(2026, 7, 25)), Instant.parse("2026-07-24T09:00:00Z"));

            mockMvc.perform(get("/notifications").header("Authorization", authHeader(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /scans/{id}/notification")
    class ToggleNotification {

        @Test
        @DisplayName("enabled=true on a future peak schedules a notification")
        void schedulesFuturePeak() throws Exception {
            String token = signupAndLogin("toggle_future@example.com", "password123");
            User user = userOf("toggle_future@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(5));

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(scan.getId()))
                    .andExpect(jsonPath("$.data.notification.status").value("scheduled"));

            assertThat(notificationRepository.findByScanId(scan.getId())).hasSize(1);
        }

        @Test
        @DisplayName("enabled=true when a notification already exists keeps it")
        void keepsExistingNotification() throws Exception {
            String token = signupAndLogin("toggle_existing@example.com", "password123");
            User user = userOf("toggle_existing@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(5));
            newNotification(user, scan, Instant.now().plusSeconds(3600));

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notification.status").value("scheduled"));

            assertThat(notificationRepository.findByScanId(scan.getId())).hasSize(1);
        }

        @Test
        @DisplayName("enabled=true on an already-passed peak is ignored")
        void ignoresPastPeak() throws Exception {
            String token = signupAndLogin("toggle_past@example.com", "password123");
            User user = userOf("toggle_past@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).minusDays(2));

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notification.status").value("none"));

            assertThat(notificationRepository.findByScanId(scan.getId())).isEmpty();
        }

        @Test
        @DisplayName("enabled=false deletes a scheduled notification")
        void deletesScheduled() throws Exception {
            String token = signupAndLogin("toggle_cancel@example.com", "password123");
            User user = userOf("toggle_cancel@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(5));
            newNotification(user, scan, Instant.now().plusSeconds(3600));

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notification.status").value("none"));

            assertThat(notificationRepository.findByScanId(scan.getId())).isEmpty();
        }

        @Test
        @DisplayName("enabled=false keeps a sent notification")
        void keepsSent() throws Exception {
            String token = signupAndLogin("toggle_keep_sent@example.com", "password123");
            User user = userOf("toggle_keep_sent@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).minusDays(1));
            Notification sent = newNotification(user, scan, Instant.now().minusSeconds(3600));
            sent.markSent(Instant.now());
            notificationRepository.save(sent);

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notification.status").value("sent"));

            assertThat(notificationRepository.findByScanId(scan.getId())).hasSize(1);
        }

        @Test
        @DisplayName("another user's scan returns 403 FORBIDDEN")
        void anotherUsersScan() throws Exception {
            signupAndLogin("toggle_owner@example.com", "password123");
            String otherToken = signupAndLogin("toggle_other@example.com", "password123");
            Scan scan = newScan(userOf("toggle_owner@example.com"), LocalDate.now(ZoneOffset.UTC).plusDays(5));

            mockMvc.perform(patch("/scans/{id}/notification", scan.getId())
                            .header("Authorization", authHeader(otherToken))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("missing scan id returns 404 NOT_FOUND")
        void missingScan() throws Exception {
            String token = signupAndLogin("toggle_missing@example.com", "password123");

            mockMvc.perform(patch("/scans/{id}/notification", 999_999L)
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(patch("/scans/{id}/notification", 1L)
                            .contentType("application/json")
                            .content(asJson(Map.of("enabled", true))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("dispatchDue")
    class DispatchDue {

        private Notification pastScheduled(User user, Scan scan) {
            return notificationRepository.save(Notification.builder()
                    .user(user)
                    .scan(scan)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .payload(Map.of("title", "t", "body", "b"))
                    .build());
        }

        @Test
        @DisplayName("sends and marks sent when the user has push enabled and a token")
        void sendsWhenEligible() throws Exception {
            signupAndLogin("dispatch_ok@example.com", "password123");
            User user = userOf("dispatch_ok@example.com");
            user.registerPushToken("device-token-123");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(3));
            Notification notification = pastScheduled(user, scan);

            notificationService.dispatchDue();

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("sent");
            assertThat(updated.getSentAt()).isNotNull();
            verify(pushSender).send(eq("device-token-123"), any());
        }

        @Test
        @DisplayName("skips a user without a push token")
        void skipsWithoutToken() throws Exception {
            signupAndLogin("dispatch_notoken@example.com", "password123");
            User user = userOf("dispatch_notoken@example.com");
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(3));
            Notification notification = pastScheduled(user, scan);

            notificationService.dispatchDue();

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("scheduled");
            verify(pushSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("skips a user with push disabled")
        void skipsWhenPushDisabled() throws Exception {
            signupAndLogin("dispatch_disabled@example.com", "password123");
            User user = userOf("dispatch_disabled@example.com");
            user.registerPushToken("device-token-456");
            user.updateSettings(null, false, null);
            Scan scan = newScan(user, LocalDate.now(ZoneOffset.UTC).plusDays(3));
            Notification notification = pastScheduled(user, scan);

            notificationService.dispatchDue();

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("scheduled");
            verify(pushSender, never()).send(any(), any());
        }
    }
}
