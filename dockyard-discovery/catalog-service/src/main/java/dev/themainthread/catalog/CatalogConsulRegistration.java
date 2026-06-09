package dev.themainthread.catalog;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class CatalogConsulRegistration {

    private static final Logger LOG = Logger.getLogger(CatalogConsulRegistration.class);

    private static final String CONSUL_SERVICE_NAME = "catalog-service";

    private final CatalogInstanceConfig instance;
    private final CatalogConsulConfig consul;
    private final Vertx vertx;
    private final int port;

    private ConsulClient consulClient;

    public CatalogConsulRegistration(
            CatalogInstanceConfig instance,
            CatalogConsulConfig consul,
            Vertx vertx,
            @ConfigProperty(name = "quarkus.http.port", defaultValue = "8081") int port) {
        this.instance = instance;
        this.consul = consul;
        this.vertx = vertx;
        this.port = port;
    }

    void onStart(@Observes StartupEvent event) {
        if (!consul.registrationEnabled()) {
            LOG.debugf("Consul registration disabled for %s instance %s", CONSUL_SERVICE_NAME, instance.id());
            return;
        }

        consulClient = ConsulClient.create(vertx, new ConsulClientOptions()
                .setHost(consul.host())
                .setPort(consul.port()));

        String healthCheckUrl = "http://" + consul.healthCheckHost() + ":" + port + "/q/health/live";
        CheckOptions check = new CheckOptions()
                .setHttp(healthCheckUrl)
                .setInterval(consul.healthCheckInterval())
                .setDeregisterAfter(consul.healthCheckDeregisterAfter());

        ServiceOptions service = new ServiceOptions()
                .setName(CONSUL_SERVICE_NAME)
                .setId(instance.id())
                .setAddress(consul.advertisedAddress())
                .setPort(port)
                .setCheckOptions(check);

        consulClient.registerServiceAndAwait(service);
        LOG.infof("Registered %s instance %s at %s:%d", CONSUL_SERVICE_NAME, instance.id(),
                consul.advertisedAddress(), port);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (!consul.registrationEnabled() || consulClient == null) {
            return;
        }

        consulClient.deregisterServiceAndAwait(instance.id());
        LOG.infof("Deregistered %s instance %s", CONSUL_SERVICE_NAME, instance.id());
    }
}
