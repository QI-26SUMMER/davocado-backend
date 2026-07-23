package com.davocado.server.domain.notification.dto;

import com.davocado.server.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.Map;

/** A single row in the {@code GET /notifications} in-app list. See API spec v1.0 section 4.1. */
public record NotificationListItem(
        Long id,
        Long scanId,
        Instant scheduledAt,
        Instant sentAt,
        String status,
        Map<String, Object> payload) {

    public static NotificationListItem of(Notification notification) {
        return new NotificationListItem(
                notification.getId(),
                notification.getScan().getId(),
                notification.getScheduledAt(),
                notification.getSentAt(),
                notification.getStatus(),
                notification.getPayload());
    }
}
