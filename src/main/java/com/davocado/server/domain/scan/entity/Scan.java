package com.davocado.server.domain.scan.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A single ripeness scan: one photo, one AI prediction, one row.
 *
 * <p>See {@code D-avocado_DB_명세서_v1.md} section 2.2. This is the app's sole core artifact —
 * avocados are not tracked as standalone entities, only as a stream of scans. {@code targetStage}
 * is a snapshot of {@code users.preferredStage} at scan time, so past scans keep a stable D-day
 * interpretation even if the user later changes their preferred stage. {@code predictedStage},
 * {@code confidence}, {@code stageProbs}, {@code daysToTarget}, {@code estimatedPeakDate} and
 * {@code modelVersion} are stored exactly as returned by the AI service — Spring does not compute
 * or override them.
 *
 * <p><b>Append-only:</b> a scan is never updated after creation ("re-scan" inserts a new row, it
 * does not modify an existing one). There is no {@code updatedAt} column and no setters.
 */
@Entity
@Table(name = "scans", indexes = @Index(name = "idx_scans_user_created", columnList = "user_id, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Snapshot of {@code users.preferredStage} at scan time; valid range 1~5. */
    @Column(name = "target_stage", nullable = false, columnDefinition = "smallint")
    private Integer targetStage;

    /** User-supplied room temperature in Celsius; stored raw, never clamped or interpreted. */
    @Column(name = "temp_celsius", precision = 4, scale = 1)
    private BigDecimal tempCelsius;

    /** Predicted ripeness stage from the model; valid range 1~5. */
    @Column(name = "predicted_stage", nullable = false, columnDefinition = "smallint")
    private Integer predictedStage;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /** Per-stage probability distribution (5 values, sums to ~1). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_probs", columnDefinition = "jsonb")
    private List<Double> stageProbs;

    /** Days remaining until {@code targetStage}; decimal and negative allowed (negative = overripe). */
    @Column(name = "days_to_target", precision = 4, scale = 1)
    private BigDecimal daysToTarget;

    @Column(name = "estimated_peak_date")
    private LocalDate estimatedPeakDate;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Scan(
            User user,
            Integer targetStage,
            BigDecimal tempCelsius,
            Integer predictedStage,
            BigDecimal confidence,
            List<Double> stageProbs,
            BigDecimal daysToTarget,
            LocalDate estimatedPeakDate,
            String modelVersion) {
        this.user = user;
        this.targetStage = targetStage;
        this.tempCelsius = tempCelsius;
        this.predictedStage = predictedStage;
        this.confidence = confidence;
        this.stageProbs = stageProbs;
        this.daysToTarget = daysToTarget;
        this.estimatedPeakDate = estimatedPeakDate;
        this.modelVersion = modelVersion;
    }
}
