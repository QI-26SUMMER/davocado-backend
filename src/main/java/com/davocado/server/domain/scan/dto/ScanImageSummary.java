package com.davocado.server.domain.scan.dto;

/**
 * The image block of a scan detail response.
 *
 * <p>{@code croppedUrl} is a TTL-signed GCS URL rather than the stored {@code gs://} path — the
 * bucket is private. It is null when the crop does not exist yet or GCS is not configured.
 */
public record ScanImageSummary(String croppedUrl) {}
