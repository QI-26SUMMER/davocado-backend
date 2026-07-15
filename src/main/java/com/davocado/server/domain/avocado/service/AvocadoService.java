package com.davocado.server.domain.avocado.service;

import com.davocado.server.domain.avocado.dto.AvocadoListItem;
import com.davocado.server.domain.avocado.dto.AvocadoListResponse;
import com.davocado.server.domain.avocado.dto.AvocadoResponse;
import com.davocado.server.domain.avocado.dto.CreateAvocadoRequest;
import com.davocado.server.domain.avocado.dto.UpdateAvocadoRequest;
import com.davocado.server.domain.avocado.entity.Avocado;
import com.davocado.server.domain.avocado.repository.AvocadoRepository;
import com.davocado.server.domain.user.repository.UserRepository;
import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles CRUD for the authenticated user's avocados. */
@Service
public class AvocadoService {

    private static final Set<String> STORAGE_CONDITIONS = Set.of("fridge", "room");
    private static final Set<String> PURPOSES = Set.of("eat_now", "in_2days", "weekend");
    private static final Set<String> STATUSES = Set.of("tracking", "consumed", "discarded");
    private static final Set<Integer> TARGET_STAGES = Set.of(3, 4);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AvocadoRepository avocadoRepository;
    private final UserRepository userRepository;

    public AvocadoService(AvocadoRepository avocadoRepository, UserRepository userRepository) {
        this.avocadoRepository = avocadoRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AvocadoResponse create(Long userId, CreateAvocadoRequest request) {
        if (!STORAGE_CONDITIONS.contains(request.storageCondition())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "storage_condition must be fridge or room");
        }
        if (request.purpose() != null && !PURPOSES.contains(request.purpose())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "purpose must be eat_now, in_2days, or weekend");
        }
        Integer targetStage = resolveTargetStage(request.targetStage(), request.purpose());

        Avocado avocado = Avocado.builder()
                .user(userRepository.getReferenceById(userId))
                .nickname(request.nickname())
                .storageCondition(request.storageCondition())
                .purpose(request.purpose())
                .targetStage(targetStage)
                .build();

        return AvocadoResponse.from(avocadoRepository.save(avocado));
    }

    @Transactional(readOnly = true)
    public AvocadoListResponse list(Long userId, String status, Integer limit, Long cursor) {
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status must be tracking, consumed, or discarded");
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        Pageable pageable = Pageable.ofSize(effectiveLimit + 1);

        List<Avocado> rows;
        if (status != null && cursor != null) {
            rows = avocadoRepository.findByUserIdAndStatusAndIdLessThanOrderByIdDesc(userId, status, cursor, pageable);
        } else if (status != null) {
            rows = avocadoRepository.findByUserIdAndStatusOrderByIdDesc(userId, status, pageable);
        } else if (cursor != null) {
            rows = avocadoRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable);
        } else {
            rows = avocadoRepository.findByUserIdOrderByIdDesc(userId, pageable);
        }

        boolean hasMore = rows.size() > effectiveLimit;
        List<Avocado> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        List<AvocadoListItem> items = page.stream().map(AvocadoListItem::from).toList();
        return new AvocadoListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public AvocadoResponse get(Long userId, Long id) {
        return AvocadoResponse.from(loadOwned(userId, id));
    }

    @Transactional
    public AvocadoResponse update(Long userId, Long id, UpdateAvocadoRequest request) {
        Avocado avocado = loadOwned(userId, id);

        if (request.storageCondition() != null && !STORAGE_CONDITIONS.contains(request.storageCondition())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "storage_condition must be fridge or room");
        }
        if (request.targetStage() != null && !TARGET_STAGES.contains(request.targetStage())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "target_stage must be 3 or 4");
        }
        if (request.status() != null && !STATUSES.contains(request.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status must be tracking, consumed, or discarded");
        }

        // TODO(step 4/5): on storageCondition/targetStage change, recompute D-day and reschedule
        // notifications; on status consumed/discarded, cancel scheduled notifications.
        avocado.updateSettings(request.storageCondition(), request.targetStage(), request.nickname());
        avocado.updateStatus(request.status());

        return AvocadoResponse.from(avocado);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Avocado avocado = loadOwned(userId, id);
        // TODO(step 4/5): predictions/images/notifications cascade once those features exist.
        avocadoRepository.delete(avocado);
    }

    private Avocado loadOwned(Long userId, Long id) {
        Avocado avocado = avocadoRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!avocado.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return avocado;
    }

    private Integer resolveTargetStage(Integer targetStage, String purpose) {
        if (targetStage != null) {
            if (!TARGET_STAGES.contains(targetStage)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "target_stage must be 3 or 4");
            }
            return targetStage;
        }
        if (purpose != null) {
            return switch (purpose) {
                case "eat_now", "in_2days" -> 3;
                case "weekend" -> 4;
                default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "purpose must be eat_now, in_2days, or weekend");
            };
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "purpose or target_stage is required");
    }
}
