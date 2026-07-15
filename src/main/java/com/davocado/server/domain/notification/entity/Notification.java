package com.davocado.server.domain.notification.entity;

import com.davocado.server.domain.avocado.entity.Avocado;
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
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A scheduled (and eventually sent) push notification for an avocado's ripeness.
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.5. Re-predicting an avocado cancels its
 * existing {@code scheduled} notifications and re-creates them, since D-day may have changed.
 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_status_scheduled_at", columnList = "status, scheduled_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avocado_id", nullable = false)
    private Avocado avocado;

    /** {@code peak_soon} / {@code peak_today} / {@code overripe_warning}. */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** {@code scheduled} / {@code sent} / {@code cancelled}. */
    @Column(nullable = false, length = 15)
    private String status;

    /** Push title/body payload. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Notification(User user, Avocado avocado, String type, Instant scheduledAt, Map<String, Object> payload) {
        this.user = user;
        this.avocado = avocado;
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.payload = payload;
        this.status = "scheduled";
    }
}
