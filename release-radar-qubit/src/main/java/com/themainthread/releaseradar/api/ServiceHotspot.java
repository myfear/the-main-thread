package com.themainthread.releaseradar.api;

public record ServiceHotspot(
        String service,
        long openIssues,
        Double averageAffectedUsers) {
}
