package com.davocado.server.domain.scan.dto;

/**
 * The image block of a scan detail response.
 *
 * <p>Both are TTL-signed GCS URLs (the bucket is private), or null when the image row / crop is
 * absent or GCS is off. {@code originalUrl} is the user's uploaded photo (shown in the UI);
 * {@code croppedUrl} is the background-removed crop the model analyzed.
 */
public record ScanImageSummary(String originalUrl, String croppedUrl) {}
