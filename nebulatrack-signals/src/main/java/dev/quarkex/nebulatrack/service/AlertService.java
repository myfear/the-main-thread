package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class AlertService {

    private final InMemoryLedger ledger;

    @Inject
    public AlertService(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void onAnomaly(@Receives CostAnomaly anomaly) {
        ledger.recordAnomaly(anomaly);
        ledger.recordAlert();
    }
}
