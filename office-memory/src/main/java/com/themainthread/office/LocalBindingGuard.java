package com.themainthread.office;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.Config;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class LocalBindingGuard {
    void verify(@Observes StartupEvent event, Config config) {
        String host = config.getValue("quarkus.http.host", String.class);
        if (!host.equals("127.0.0.1")) {
            throw new IllegalStateException("Office Memory has no authentication. Set quarkus.http.host=127.0.0.1.");
        }
    }
}
