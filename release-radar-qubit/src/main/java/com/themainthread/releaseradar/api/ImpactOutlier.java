package com.themainthread.releaseradar.api;

public record ImpactOutlier(
        String key,
        String service,
        int affectedUsers) {
}
