package com.davocado.server.domain.scan.dto;

import com.davocado.server.domain.notification.entity.Notification;

/**
 * Notification state for one scan, driving the bell icon in History. See API spec v1.0 section 3.2.
 *
 * <p>{@code status} is {@code scheduled} / {@code sent} / {@code none} — a scan has at most one
 * notification, and {@code none} means it has no notification at all.
 */
public record NotificationSummary(String status) {

    private static final NotificationSummary NONE = new NotificationSummary("none");

    public static NotificationSummary none() {
        return NONE;
    }

    public static NotificationSummary from(Notification notification) {
        return notification == null ? NONE : new NotificationSummary(notification.getStatus());
    }
}
