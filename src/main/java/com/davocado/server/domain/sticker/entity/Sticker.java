package com.davocado.server.domain.sticker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Master list of collectible stickers (gamification).
 *
 * <p>See {@code D-avocado_DB_명세서_v0.2.md} section 2.7.
 */
@Entity
@Table(name = "stickers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** Human-readable description of how this sticker is earned. */
    @Column(name = "condition", length = 100)
    private String condition;

    @Builder
    public Sticker(String code, String name, String condition) {
        this.code = code;
        this.name = name;
        this.condition = condition;
    }
}
