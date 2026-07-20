package com.davocado.server.domain.notification.entity;

import com.davocado.server.domain.scan.entity.Scan;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A scheduled (and eventually sent) push notification for a scan approaching its target ripeness.
 *
 * <p>See {@code D-avocado_DB_명세서_v1.md} section 2.4. At most one notification per scan.
 * {@code scheduledAt} is {@code estimatedPeakDate} minus the user's {@code advanceNoticeDays};
 * delivery is a real FCM/APNs push sent to {@code users.pushToken}. Deleting the parent scan
 * cascades and removes its notifications, {@code sent} rows included.
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
    @JoinColumn(name = "scan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Scan scan;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** {@code scheduled} / {@code sent}. */
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
    public Notification(User user, Scan scan, Instant scheduledAt, Map<String, Object> payload) {
        this.user = user;
        this.scan = scan;
        this.scheduledAt = scheduledAt;
        this.payload = payload;
        this.status = "scheduled";
    }

    /** Records delivery. The scheduler calls this once the push has been handed off. */
    public void markSent(Instant sentAt) {
        this.status = "sent";
        this.sentAt = sentAt;
    }
}
