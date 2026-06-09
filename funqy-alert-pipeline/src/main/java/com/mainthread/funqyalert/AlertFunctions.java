package com.mainthread.funqyalert;

import org.jboss.logging.Logger;

import io.quarkus.funqy.Context;
import io.quarkus.funqy.Funq;
import io.quarkus.funqy.knative.events.CloudEvent;
import io.quarkus.funqy.knative.events.CloudEventMapping;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AlertFunctions {

    private static final Logger LOG = Logger.getLogger(AlertFunctions.class);

    private final AlertPipelineService pipeline;

    public AlertFunctions(AlertPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Funq
    public RoutingDecision previewAlert(AlertEnvelope alert) {
        LOG.infof("previewAlert for service=%s env=%s", alert.getService(), alert.getEnvironment());
        return pipeline.preview(alert);
    }

    @Funq
    public AlertEnvelope ingestAlert(AlertEnvelope alert) {
        LOG.infof("ingestAlert for service=%s", alert.getService());
        return pipeline.ingest(alert);
    }

    @Funq
    public AlertEnvelope scoreAlert(AlertEnvelope alert) {
        LOG.infof("scoreAlert for dedupeKey=%s", alert.getDedupeKey());
        return pipeline.score(alert);
    }

    @Funq
    @CloudEventMapping(trigger = "com.mainthread.alert.scored", responseSource = "routeAlert", responseType = "com.mainthread.alert.routed")
    public RoutingDecision routeAlert(AlertEnvelope alert, @Context CloudEvent cloudEvent) {
        LOG.infof("routeAlert from source=%s", cloudEvent.source());
        return pipeline.route(alert, cloudEvent.id(), cloudEvent.source());
    }
}
