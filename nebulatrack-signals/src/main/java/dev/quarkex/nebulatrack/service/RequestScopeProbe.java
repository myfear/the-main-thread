package dev.quarkex.nebulatrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.support.InMemoryLedger;
import dev.quarkex.nebulatrack.support.InvocationTrace;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class RequestScopeProbe {

    private final InMemoryLedger ledger;
    private final InvocationTrace trace;

    @Inject
    public RequestScopeProbe(InMemoryLedger ledger, InvocationTrace trace) {
        this.ledger = ledger;
        this.trace = trace;
    }

    void onAnomaly(@Receives CostAnomaly anomaly) {
        ledger.recordRequestScopeId(trace.id());
    }
}
