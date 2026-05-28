package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.qualifier.Critical;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class CriticalLaneReceiver {

    private final InMemoryLedger ledger;

    @Inject
    public CriticalLaneReceiver(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void critical(@Receives @Critical CostAnomaly anomaly) {
        ledger.recordCriticalLane();
    }
}
