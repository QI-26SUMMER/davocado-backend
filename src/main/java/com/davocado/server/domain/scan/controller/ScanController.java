package com.davocado.server.domain.scan.controller;

import com.davocado.server.domain.scan.dto.ScanListResponse;
import com.davocado.server.domain.scan.dto.ScanNotificationRequest;
import com.davocado.server.domain.scan.dto.ScanNotificationResponse;
import com.davocado.server.domain.scan.dto.ScanResponse;
import com.davocado.server.domain.scan.dto.ScanStatsResponse;
import com.davocado.server.domain.scan.service.ScanService;
import com.davocado.server.global.auth.CurrentUserId;
import com.davocado.server.global.common.ApiResponse;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScanResponse>> create(
            @CurrentUserId Long userId,
            @RequestPart("image") MultipartFile image,
            @RequestPart("source") String source,
            @RequestPart(name = "temp_celsius", required = false) String tempCelsius)
            throws IOException {
        if (image.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "image must not be empty");
        }
        ScanResponse response =
                scanService.create(userId, image.getBytes(), source, parseTemperature(tempCelsius));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** Multipart parts arrive as text, so the optional temperature is parsed by hand. */
    private BigDecimal parseTemperature(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "temp_celsius must be a number");
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ScanListResponse>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long cursor) {
        return ResponseEntity.ok(ApiResponse.success(scanService.list(userId, limit, cursor)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ScanStatsResponse>> stats(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(scanService.stats(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScanResponse>> get(@CurrentUserId Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(scanService.get(userId, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        scanService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/notification")
    public ResponseEntity<ApiResponse<ScanNotificationResponse>> toggleNotification(
            @CurrentUserId Long userId, @PathVariable Long id, @Valid @RequestBody ScanNotificationRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(scanService.toggleNotification(userId, id, request.enabled())));
    }
}
