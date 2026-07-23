package com.davocado.server.domain.notification.dto;

import java.util.List;

/** Response body for {@code GET /notifications}. */
public record NotificationListResponse(List<NotificationListItem> items, Long nextCursor) {}
