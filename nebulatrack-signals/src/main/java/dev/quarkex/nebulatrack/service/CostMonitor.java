package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.model.Severity;
import io.quarkus.signals.Signal;

@ApplicationScoped
public class CostMonitor {

    private final Signal<CostAnomaly> anomalySignal;

    @Inject
    public CostMonitor(Signal<CostAnomaly> anomalySignal) {
        this.anomalySignal = anomalySignal;
    }

    public void detect() {
        anomalySignal.publish(new CostAnomaly("us-east-1", 340.0, Severity.NORMAL));
    }

    public void detectWithMetadata(String traceId, String tenant) {
        anomalySignal.withMetadata("traceId", traceId)
                .withMetadata("tenant", tenant)
                .publish(new CostAnomaly("us-east-1", 340.0, Severity.NORMAL));
    }
}
