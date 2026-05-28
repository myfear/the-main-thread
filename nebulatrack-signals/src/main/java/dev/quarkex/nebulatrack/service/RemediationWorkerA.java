package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.RemediationRequest;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class RemediationWorkerA {

    private final InMemoryLedger ledger;

    @Inject
    public RemediationWorkerA(InMemoryLedger ledger) {
        this.ledger = ledger;
    }

    void handle(@Receives RemediationRequest request) {
        ledger.recordWorkerA();
    }
}
