package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;
import io.quarkus.signals.SignalContext;

@ApplicationScoped
public class AuditTrail {

    private final InMemoryLedger ledger;

    @Inject
    public AuditTrail(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void onAnomaly(@Receives SignalContext<dev.quarkex.nebulatrack.model.CostAnomaly> ctx) {
        ledger.recordAudit();
        ledger.recordMetadata(ctx.metadata());
    }
}
