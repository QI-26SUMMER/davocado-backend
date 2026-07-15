package com.davocado.server.domain.sticker.entity;

import com.davocado.server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Record of a sticker earned by a user.
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.7. Earned automatically by the server
 * when a qualifying event happens (e.g. hitting the peak day) — never created by the client.
 */
@Entity
@Table(
        name = "user_stickers",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_stickers_user_sticker", columnNames = {"user_id", "sticker_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserSticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sticker_id", nullable = false)
    private Sticker sticker;

    @CreatedDate
    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    @Builder
    public UserSticker(User user, Sticker sticker) {
        this.user = user;
        this.sticker = sticker;
    }
}
