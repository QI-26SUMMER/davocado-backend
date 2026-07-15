package com.davocado.server.domain.prediction.entity;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A captured image (raw and/or cropped) belonging to a prediction, stored in GCS.
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.4. Only the GCS paths are stored here;
 * {@code croppedUrl} is converted to a TTL-signed URL at the API layer before being returned.
 */
@Entity
@Table(name = "images", indexes = @Index(name = "idx_images_prediction", columnList = "prediction_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id", nullable = false)
    private Prediction prediction;

    /** Raw image GCS path, e.g. {@code gs://d-avocado-images/raw/{avocado_id}/{prediction_id}/{side}.jpg}. */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** Cropped/normalized image GCS path. */
    @Column(name = "cropped_url", length = 500)
    private String croppedUrl;

    /** {@code a} / {@code b} / {@code single}. */
    @Column(nullable = false, length = 10)
    private String side;

    /** {@code camera} / {@code gallery}. */
    @Column(nullable = false, length = 10)
    private String source;

    private Integer width;

    private Integer height;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Image(Prediction prediction, String imageUrl, String croppedUrl, String side, String source,
            Integer width, Integer height) {
        this.prediction = prediction;
        this.imageUrl = imageUrl;
        this.croppedUrl = croppedUrl;
        this.side = side;
        this.source = source;
        this.width = width;
        this.height = height;
    }
}
