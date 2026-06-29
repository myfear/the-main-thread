package io.mainthread.vaultboard.dashboard;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DashboardService {

    public List<Dashboard> listAll() {
        return Dashboard.listAll();
    }

    @Transactional
    public Dashboard create(CreateDashboardRequest request) {
        Dashboard dashboard = new Dashboard();
        dashboard.name = request.name();
        dashboard.ownerEmail = request.ownerEmail();
        dashboard.monthlyBudget = request.monthlyBudget();
        dashboard.persist();
        return dashboard;
    }
}
