package com.themainthread.flyway.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "migration-demo")
public interface MigrationDemoConfig {

    @WithDefault("MODERN")
    Release release();

    Optional<String> schemaTarget();

    enum Release {
        LEGACY,
        BRIDGE,
        MODERN
    }
}
