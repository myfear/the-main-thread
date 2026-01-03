package com.example.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        String nextCursor,
        String prevCursor,
        boolean hasNext,
        boolean hasPrev,
        int count) {
}