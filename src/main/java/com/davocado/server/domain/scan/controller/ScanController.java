package com.davocado.server.domain.scan.controller;

import com.davocado.server.domain.scan.dto.ScanListResponse;
import com.davocado.server.domain.scan.dto.ScanResponse;
import com.davocado.server.domain.scan.dto.ScanStatsResponse;
import com.davocado.server.domain.scan.service.ScanService;
import com.davocado.server.global.auth.CurrentUserId;
import com.davocado.server.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
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
}
