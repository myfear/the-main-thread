package com.themainthread.codemode;

import com.fasterxml.jackson.databind.JsonNode;

public record ExecutionResult(boolean ok, JsonNode value, Failure error, Metrics metrics) {
    public record Failure(String kind, String message) {
    }

    public record Metrics(long durationMs, int nestedCalls, long workerPid, long upstreamPid,
            boolean workerReady, boolean cleanedUp) {
    }
}
