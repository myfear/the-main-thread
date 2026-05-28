package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.model.Severity;
import dev.quarkex.nebulatrack.qualifier.Critical;
import io.quarkus.signals.Signal;

@ApplicationScoped
public class CriticalAnomalyEmitter {

    private final Signal<CostAnomaly> defaultAnomalySignal;
    private final Signal<CostAnomaly> anyAnomalySignal;

    @Inject
    public CriticalAnomalyEmitter(
            Signal<CostAnomaly> defaultAnomalySignal,
            @Any Signal<CostAnomaly> anyAnomalySignal) {
        this.defaultAnomalySignal = defaultAnomalySignal;
        this.anyAnomalySignal = anyAnomalySignal;
    }

    public void publishCritical() {
        anyAnomalySignal.select(Critical.Literal.INSTANCE)
                .publish(new CostAnomaly("eu-west-1", 900.0, Severity.CRITICAL));
    }

    public void publishDefault() {
        defaultAnomalySignal.publish(new CostAnomaly("us-west-2", 120.0, Severity.NORMAL));
    }
}
