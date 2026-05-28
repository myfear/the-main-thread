package dev.quarkex.nebulatrack.service;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostEstimate;
import dev.quarkex.nebulatrack.model.EstimateRequest;
import io.quarkus.signals.Signal;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BudgetService {

    private final Signal<EstimateRequest> estimateSignal;

    @Inject
    public BudgetService(Signal<EstimateRequest> estimateSignal) {
        this.estimateSignal = estimateSignal;
    }

    public Uni<CostEstimate> estimateReactive(String service, int units) {
        return estimateSignal.reactive()
                .request(new EstimateRequest(service, units), CostEstimate.class);
    }

    public CostEstimate estimateBlocking(String service, int units) {
        return estimateSignal.request(new EstimateRequest(service, units), CostEstimate.class);
    }
}
