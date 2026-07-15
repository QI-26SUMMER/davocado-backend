package com.davocado.server.domain.avocado.entity;

import com.davocado.server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A single avocado tracked by a user over time (storage setting, target stage, latest state).
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.2. Allowed values for
 * {@code storageCondition} / {@code purpose} / {@code status} are listed in section 3.
 */
@Entity
@Table(name = "avocados", indexes = @Index(name = "idx_avocados_user_status", columnList = "user_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Avocado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String nickname;

    /** {@code fridge} / {@code room}. */
    @Column(name = "storage_condition", nullable = false, length = 10)
    private String storageCondition;

    /** {@code eat_now} / {@code in_2days} / {@code weekend}. */
    @Column(length = 20)
    private String purpose;

    /** Ripeness target stage; valid range 3~4. */
    @Column(name = "target_stage", nullable = false, columnDefinition = "smallint")
    private Integer targetStage;

    /** Latest predicted stage, denormalized from {@code predictions}; valid range 1~5. */
    @Column(name = "current_stage", columnDefinition = "smallint")
    private Integer currentStage;

    /** {@code tracking} / {@code consumed} / {@code discarded}. */
    @Column(nullable = false, length = 15)
    private String status;

    @Column(name = "last_predicted_at")
    private Instant lastPredictedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Avocado(User user, String nickname, String storageCondition, String purpose, Integer targetStage) {
        this.user = user;
        this.nickname = nickname;
        this.storageCondition = storageCondition;
        this.purpose = purpose;
        this.targetStage = targetStage;
        this.status = "tracking";
    }
}
