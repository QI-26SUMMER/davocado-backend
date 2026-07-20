package com.davocado.server.global.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the GCS client only when a bucket is configured.
 *
 * <p>Without this guard the client would try to resolve Application Default Credentials at startup
 * and fail wherever GCP is not provisioned — tests and fresh checkouts included.
 */
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    @Bean
    @ConditionalOnExpression("!'${gcs.bucket:}'.isEmpty()")
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
