package com.davocado.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so entities can auto-populate {@code @CreatedDate} /
 * {@code @LastModifiedDate} fields (added when entities are introduced).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
