package dev.forgeassist;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

@ApplicationScoped
public class RoutingEventObserver {

    private static final Logger LOG = Logger.getLogger(RoutingEventObserver.class);

    public void onRoutingDecision(@ObservesAsync RoutingDecision decision) {
        LOG.infof(
                "[ROUTING] complexity=%s model=%s classificationMs=%d prompt=\"%s\"",
                decision.complexity(),
                decision.selectedModel(),
                decision.classificationMillis(),
                truncate(decision.prompt(), 80));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}