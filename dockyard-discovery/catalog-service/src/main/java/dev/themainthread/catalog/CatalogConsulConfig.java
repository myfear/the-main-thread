package dev.themainthread.catalog;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "catalog.consul")
public interface CatalogConsulConfig {

    @WithDefault("true")
    boolean registrationEnabled();

    @WithDefault("localhost")
    String host();

    @WithDefault("8500")
    int port();

    @WithDefault("127.0.0.1")
    String advertisedAddress();

    @WithDefault("host.containers.internal")
    String healthCheckHost();

    @WithDefault("5s")
    String healthCheckInterval();

    @WithDefault("20s")
    String healthCheckDeregisterAfter();
}
