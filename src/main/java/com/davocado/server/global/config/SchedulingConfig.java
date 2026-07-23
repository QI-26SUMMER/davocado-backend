package com.davocado.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} tasks (the notification dispatch timer) outside the {@code test}
 * profile.
 *
 * <p>Integration tests activate the {@code test} profile, so the timer never fires during a test
 * run - assertions on {@code scheduled}/{@code sent} counts stay deterministic. The scheduler's
 * logic is still exercised by calling {@code NotificationService.dispatchDue()} directly.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
