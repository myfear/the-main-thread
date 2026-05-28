package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.RemediationRequest;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class RemediationWorkerB {

    private final InMemoryLedger ledger;

    @Inject
    public RemediationWorkerB(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void handle(@Receives RemediationRequest request) {
        ledger.recordWorkerB();
    }
}
