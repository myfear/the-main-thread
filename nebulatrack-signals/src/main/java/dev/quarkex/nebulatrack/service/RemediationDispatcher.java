package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.model.RemediationRequest;
import io.quarkus.signals.Signal;

@ApplicationScoped
public class RemediationDispatcher {

    private final Signal<RemediationRequest> remediationSignal;

    @Inject
    public RemediationDispatcher(Signal<RemediationRequest> remediationSignal) {
        this.remediationSignal = remediationSignal;
    }

    public void dispatch(CostAnomaly anomaly) {
        remediationSignal.send(new RemediationRequest(anomaly.region(), "scale-down-idle-nodes"));
    }

    public void dispatch(String region, String action) {
        remediationSignal.send(new RemediationRequest(region, action));
    }
}
