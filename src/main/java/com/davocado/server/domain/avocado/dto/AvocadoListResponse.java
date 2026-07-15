package com.davocado.server.domain.avocado.dto;

import java.util.List;

/** Response body for {@code GET /avocados}: cursor-paginated list. */
public record AvocadoListResponse(List<AvocadoListItem> items, Long nextCursor) {}
