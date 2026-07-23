package com.davocado.server.domain.scan.dto;

/** Response body for {@code PATCH /scans/{id}/notification}. See API spec v1.0 section 3.6. */
public record ScanNotificationResponse(Long id, NotificationSummary notification) {

    public static ScanNotificationResponse of(Long id, NotificationSummary notification) {
        return new ScanNotificationResponse(id, notification);
    }
}
