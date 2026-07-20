package com.davocado.server.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * A registered user (self-signup with email / password).
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.1.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    private static final int DEFAULT_PREFERRED_STAGE = 3;
    private static final int DEFAULT_ADVANCE_NOTICE_DAYS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 50)
    private String nickname;

    /** Global target ripeness ("Preferred Ripeness"); valid range 1~5. Snapshotted onto each scan. */
    @Column(name = "preferred_stage", nullable = false, columnDefinition = "smallint")
    private Integer preferredStage = DEFAULT_PREFERRED_STAGE;

    /** Master toggle for push notifications. */
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    /** How many days before the target to notify; 0 = same day, valid range 0~3. */
    @Column(name = "advance_notice_days", nullable = false, columnDefinition = "smallint")
    private Integer advanceNoticeDays = DEFAULT_ADVANCE_NOTICE_DAYS;

    /** FCM/APNs token, registered by the app on launch and cleared on logout. */
    @Column(name = "push_token", length = 255)
    private String pushToken;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /** Records a successful login at the given instant. */
    public void updateLastLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /** Updates only the non-null fields, leaving the rest unchanged. */
    public void updateProfile(String nickname) {
        if (nickname != null) {
            this.nickname = nickname;
        }
    }

    /** Updates the Settings values; only the non-null fields are applied. */
    public void updateSettings(Integer preferredStage, Boolean pushEnabled, Integer advanceNoticeDays) {
        if (preferredStage != null) {
            this.preferredStage = preferredStage;
        }
        if (pushEnabled != null) {
            this.pushEnabled = pushEnabled;
        }
        if (advanceNoticeDays != null) {
            this.advanceNoticeDays = advanceNoticeDays;
        }
    }

    /** Stores the device push token; re-registering simply overwrites it. */
    public void registerPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    /** Clears the push token so the device stops receiving notifications. */
    public void clearPushToken() {
        this.pushToken = null;
    }
}
