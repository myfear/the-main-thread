package com.themainthread.flyway.config;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import io.quarkus.flyway.FlywayConfigurationCustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MigrationTargetCustomizer implements FlywayConfigurationCustomizer {

    private final MigrationDemoConfig config;

    @Inject
    public MigrationTargetCustomizer(MigrationDemoConfig config) {
        this.config = config;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        config.schemaTarget()
                .map(MigrationVersion::fromVersion)
                .ifPresent(configuration::target);
    }
}
