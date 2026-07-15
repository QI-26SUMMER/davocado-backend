package com.davocado.server.domain.prediction.entity;

import com.davocado.server.domain.avocado.entity.Avocado;
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
 * A single ripeness prediction for an avocado, as returned by the AI service.
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.3. {@code predictedStage},
 * {@code stageProbs}, {@code daysToTarget}, {@code estimatedPeakDate} and {@code modelVersion}
 * are stored exactly as returned by the AI service — Spring does not compute or override them.
 */
@Entity
@Table(
        name = "predictions",
        indexes = @Index(name = "idx_predictions_avocado_predicted_at", columnList = "avocado_id, predicted_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avocado_id", nullable = false)
    private Avocado avocado;

    /** Predicted ripeness stage from the model; valid range 1~5. */
    @Column(name = "predicted_stage", nullable = false, columnDefinition = "smallint")
    private Integer predictedStage;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /** Per-stage probability distribution (5 values, sums to ~1). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_probs", columnDefinition = "jsonb")
    private List<Double> stageProbs;

    @Column(name = "days_to_target", columnDefinition = "smallint")
    private Integer daysToTarget;

    @Column(name = "estimated_peak_date")
    private LocalDate estimatedPeakDate;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @CreatedDate
    @Column(name = "predicted_at", nullable = false, updatable = false)
    private Instant predictedAt;

    @Builder
    public Prediction(
            Avocado avocado,
            Integer predictedStage,
            BigDecimal confidence,
            List<Double> stageProbs,
            Integer daysToTarget,
            LocalDate estimatedPeakDate,
            String modelVersion) {
        this.avocado = avocado;
        this.predictedStage = predictedStage;
        this.confidence = confidence;
        this.stageProbs = stageProbs;
        this.daysToTarget = daysToTarget;
        this.estimatedPeakDate = estimatedPeakDate;
        this.modelVersion = modelVersion;
    }
}
