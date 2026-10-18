package com.themainthread.codemode;

import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "gateway")
public interface GatewayConfig {
    @WithDefault(".")
    Path project();

    @WithDefault(".local/session")
    Path session();

    Optional<Path> upstreamJar();

    Optional<Path> jbang();

    @WithDefault("false")
    boolean startDemo();

    @WithDefault("18191")
    int demoPort();

    @WithDefault("15000")
    long toolTimeoutMs();

    @WithDefault("60000")
    long executionTimeoutMs();
}
