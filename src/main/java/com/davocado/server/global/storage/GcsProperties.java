package com.davocado.server.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GCS settings. Credentials are not here on purpose — the client picks them up from
 * {@code GOOGLE_APPLICATION_CREDENTIALS} (Application Default Credentials).
 *
 * @param bucket private bucket holding {@code raw/} and {@code cropped/}; blank disables GCS
 * @param signedUrlTtlMinutes how long an issued signed URL stays valid
 */
@ConfigurationProperties(prefix = "gcs")
public record GcsProperties(String bucket, int signedUrlTtlMinutes) {}
