package com.davocado.server.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI document metadata. UI is served at {@code /swagger-ui.html},
 * spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI davocadoOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("D-avocado API")
                .description("Avocado ripening tracker backend (auth, CRUD, D-day, notifications).")
                .version("v0.0.1"));
    }
}
