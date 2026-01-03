package com.example.pagination;

import java.util.Objects;

public record CursorPayload(
        String sort,
        String category,
        String q,
        String direction,
        long k1,
        long k2) {
    public CursorPayload {
        sort = Objects.requireNonNull(sort, "sort");
        direction = Objects.requireNonNull(direction, "direction");
        category = category == null ? "" : category;
        q = q == null ? "" : q;
    }
}