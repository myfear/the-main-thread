package com.orderbridge;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownDelayInitiatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ShutdownLifecycle {

    private static final Logger LOG = Logger.getLogger(ShutdownLifecycle.class);

    void onStart(@Observes StartupEvent event) {
        LOG.info("OrderBridge is ready to accept traffic");
    }

    void onShutdownDelay(@Observes ShutdownDelayInitiatedEvent event) {
        LOG.info("Shutdown delay started — readiness should fail while HTTP still winds down");
    }

    void onStop(@Observes ShutdownEvent event) {
        LOG.info("OrderBridge shutdown event fired");
    }
}
