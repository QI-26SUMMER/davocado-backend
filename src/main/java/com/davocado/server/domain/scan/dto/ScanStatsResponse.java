package com.davocado.server.domain.scan.dto;

/**
 * Response body for {@code GET /scans/stats} — the cards above History.
 *
 * <p>{@code total} counts scans; {@code notified} and {@code pending} count the caller's
 * {@code sent} and {@code scheduled} notifications. See API spec v1.0 section 3.3.
 */
public record ScanStatsResponse(long total, long notified, long pending) {}
