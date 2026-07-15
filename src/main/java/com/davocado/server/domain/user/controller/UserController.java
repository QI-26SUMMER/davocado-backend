package com.davocado.server.domain.user.controller;

import com.davocado.server.domain.user.dto.UpdateMeRequest;
import com.davocado.server.domain.user.dto.UserResponse;
import com.davocado.server.domain.user.service.UserService;
import com.davocado.server.global.auth.CurrentUserId;
import com.davocado.server.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @CurrentUserId Long userId, @Valid @RequestBody UpdateMeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateMe(userId, request)));
    }
}
