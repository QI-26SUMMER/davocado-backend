package com.davocado.server.domain.scan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A captured image belonging to a scan, stored in GCS.
 *
 * <p>See {@code D-avocado_DB_명세서_v1.md} section 2.3. Only the GCS paths are stored here;
 * {@code croppedUrl} is converted to a TTL-signed URL at the API layer before being returned.
 * Exactly one image per scan (single capture), enforced by the unique {@code scan_id}.
 */
@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Scan scan;

    /** Raw image GCS path, e.g. {@code gs://d-avocado-images/raw/{user_id}/{scan_id}.jpg}. */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /**
     * Cropped/normalized image GCS path, e.g.
     * {@code gs://d-avocado-images/cropped/{user_id}/{scan_id}.jpg}. Null until the AI service
     * returns a crop (and stays null if cropping failed).
     */
    @Column(name = "cropped_url", length = 500)
    private String croppedUrl;

    /** {@code camera} / {@code gallery}. */
    @Column(nullable = false, length = 10)
    private String source;

    private Integer width;

    private Integer height;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Image(Scan scan, String imageUrl, String croppedUrl, String source, Integer width, Integer height) {
        this.scan = scan;
        this.imageUrl = imageUrl;
        this.croppedUrl = croppedUrl;
        this.source = source;
        this.width = width;
        this.height = height;
    }
}
