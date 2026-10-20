package com.themainthread.time;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "advent")
public interface AdventConfig {
    int year();
}
