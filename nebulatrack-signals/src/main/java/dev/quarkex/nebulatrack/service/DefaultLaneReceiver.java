package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class DefaultLaneReceiver {

    private final InMemoryLedger ledger;

    @Inject
    public DefaultLaneReceiver(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void general(@Receives CostAnomaly anomaly) {
        ledger.recordDefaultLane();
    }
}
