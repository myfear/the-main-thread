package io.mainthread.vaultboard.dashboard;

import java.math.BigDecimal;

public record CreateDashboardRequest(
        String name,
        String ownerEmail,
        BigDecimal monthlyBudget) {
}
