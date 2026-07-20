package com.davocado.server.domain.user.service;

import com.davocado.server.domain.user.dto.PushTokenRequest;
import com.davocado.server.domain.user.dto.PushTokenResponse;
import com.davocado.server.domain.user.dto.SettingsResponse;
import com.davocado.server.domain.user.dto.UpdateMeRequest;
import com.davocado.server.domain.user.dto.UpdateSettingsRequest;
import com.davocado.server.domain.user.dto.UserResponse;
import com.davocado.server.domain.user.entity.User;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles reads/updates for the authenticated user's own profile. */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.updateProfile(request.nickname());
        return UserResponse.from(user);
    }

    @Transactional
    public SettingsResponse updateSettings(Long userId, UpdateSettingsRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.updateSettings(request.preferredStage(), request.pushEnabled(), request.advanceNoticeDays());
        return SettingsResponse.from(user);
    }

    @Transactional
    public PushTokenResponse registerPushToken(Long userId, PushTokenRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.registerPushToken(request.pushToken());
        return PushTokenResponse.registered();
    }
}
