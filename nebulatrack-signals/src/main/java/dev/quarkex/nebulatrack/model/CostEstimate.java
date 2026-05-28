package dev.quarkex.nebulatrack.model;

import java.math.BigDecimal;

public record CostEstimate(String service, int units, BigDecimal monthlyCost) {
}
