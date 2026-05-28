package dev.quarkex.nebulatrack.service;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;

import dev.quarkex.nebulatrack.model.CostEstimate;
import dev.quarkex.nebulatrack.model.EstimateRequest;
import io.quarkus.signals.Receives;

@ApplicationScoped
public class PricingEngine {

    CostEstimate onEstimate(@Receives EstimateRequest request) {
        BigDecimal monthlyCost = BigDecimal.valueOf(request.units()).multiply(BigDecimal.valueOf(0.12));
        return new CostEstimate(request.service(), request.units(), monthlyCost);
    }
}
