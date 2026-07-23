package com.davocado.server.domain.notification.controller;

import com.davocado.server.domain.notification.dto.NotificationListResponse;
import com.davocado.server.domain.notification.service.NotificationService;
import com.davocado.server.global.auth.CurrentUserId;
import com.davocado.server.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long cursor) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.list(userId, status, limit, cursor)));
    }
}
