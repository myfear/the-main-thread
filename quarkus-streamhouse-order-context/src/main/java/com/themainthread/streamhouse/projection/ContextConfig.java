package com.themainthread.streamhouse.projection;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "context")
public interface ContextConfig {
    Duration heartbeatMaxAge();

    Duration heartbeatFutureSkew();
}
