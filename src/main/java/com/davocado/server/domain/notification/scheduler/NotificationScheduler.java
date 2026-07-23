package com.davocado.server.domain.notification.scheduler;

import com.davocado.server.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically dispatches due notifications. See {@link NotificationService#dispatchDue()}. */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationService notificationService;

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Runs once a minute; a defensive catch keeps one bad run from killing the scheduler thread. */
    @Scheduled(fixedDelay = 60_000L)
    public void dispatchDueNotifications() {
        try {
            notificationService.dispatchDue();
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch due notifications", ex);
        }
    }
}
