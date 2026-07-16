package com.themainthread.releaseradar.api;

import java.time.LocalDateTime;

import com.themainthread.releaseradar.domain.IssueSeverity;

public record BlockerView(
        String key,
        String service,
        IssueSeverity severity,
        LocalDateTime openedAt,
        int affectedUsers) {
}
