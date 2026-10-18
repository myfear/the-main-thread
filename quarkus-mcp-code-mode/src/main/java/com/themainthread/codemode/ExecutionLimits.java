package com.themainthread.codemode;

import java.time.Duration;

public record ExecutionLimits(int scriptBytes, int inputBytes, int calls, Duration toolTimeout,
        Duration executionTimeout, int resultBytes, int upstreamBytes) {
    public ExecutionLimits {
        if (scriptBytes < 1 || scriptBytes > 32768 || inputBytes < 1 || inputBytes > 32768
                || calls < 1 || calls > 10 || resultBytes < 1 || resultBytes > 16384
                || upstreamBytes < 1 || upstreamBytes > 65536 || toolTimeout.isNegative()
                || toolTimeout.toMillis() < 1 || executionTimeout.isNegative() || executionTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Invalid feasibility limits");
        }
    }

    public static ExecutionLimits defaults() {
        return new ExecutionLimits(32768, 32768, 10, Duration.ofSeconds(15), Duration.ofSeconds(60), 16384, 65536);
    }
}
