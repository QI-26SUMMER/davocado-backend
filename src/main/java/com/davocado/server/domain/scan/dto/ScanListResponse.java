package com.davocado.server.domain.scan.dto;

import java.util.List;

/** Response body for {@code GET /scans}. */
public record ScanListResponse(List<ScanListItem> items, Long nextCursor) {}
