package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class CatchAllAnomalyReceiver {

    private final InMemoryLedger ledger;

    @Inject
    public CatchAllAnomalyReceiver(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void catchAll(@Receives @Any CostAnomaly anomaly) {
        ledger.recordCatchAll();
    }
}
