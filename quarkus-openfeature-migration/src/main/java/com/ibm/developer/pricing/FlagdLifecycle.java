package com.ibm.developer.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class FlagdLifecycle {

    private static final Logger LOG = Logger.getLogger(FlagdLifecycle.class);

    private final FlagdConfig config;
    private boolean providerRegistered;

    public FlagdLifecycle(FlagdConfig config) {
        this.config = config;
    }

    void onStart(@Observes StartupEvent event) {
        if (!config.enabled()) {
            return;
        }

        FlagdOptions options = FlagdOptions.builder()
                .host(config.host())
                .port(config.port())
                .build();

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(options));
        providerRegistered = true;
        LOG.infof("Registered the flagd provider at %s:%d", config.host(), config.port());
    }

    void onStop(@Observes ShutdownEvent event) {
        if (providerRegistered) {
            OpenFeatureAPI.getInstance().shutdown();
        }
    }
}
