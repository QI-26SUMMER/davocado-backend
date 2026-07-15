package com.davocado.server.domain.avocado.controller;

import com.davocado.server.domain.avocado.dto.AvocadoListResponse;
import com.davocado.server.domain.avocado.dto.AvocadoResponse;
import com.davocado.server.domain.avocado.dto.CreateAvocadoRequest;
import com.davocado.server.domain.avocado.dto.UpdateAvocadoRequest;
import com.davocado.server.domain.avocado.service.AvocadoService;
import com.davocado.server.global.auth.CurrentUserId;
import com.davocado.server.global.common.ApiResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avocados")
public class AvocadoController {

    private final AvocadoService avocadoService;

    public AvocadoController(AvocadoService avocadoService) {
        this.avocadoService = avocadoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AvocadoResponse>> create(
            @CurrentUserId Long userId, @Valid @RequestBody CreateAvocadoRequest request) {
        AvocadoResponse response = avocadoService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AvocadoListResponse>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long cursor) {
        return ResponseEntity.ok(ApiResponse.success(avocadoService.list(userId, status, limit, cursor)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AvocadoResponse>> get(@CurrentUserId Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(avocadoService.get(userId, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AvocadoResponse>> update(
            @CurrentUserId Long userId, @PathVariable Long id, @Valid @RequestBody UpdateAvocadoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(avocadoService.update(userId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        avocadoService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
