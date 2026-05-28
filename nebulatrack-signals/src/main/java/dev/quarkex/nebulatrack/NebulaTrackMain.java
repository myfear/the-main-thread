package dev.quarkex.nebulatrack;

import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.service.BudgetService;
import dev.quarkex.nebulatrack.service.CostMonitor;
import dev.quarkex.nebulatrack.service.RemediationDispatcher;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class NebulaTrackMain implements QuarkusApplication {

    private final CostMonitor costMonitor;
    private final RemediationDispatcher remediationDispatcher;
    private final BudgetService budgetService;

    @Inject
    public NebulaTrackMain(
            CostMonitor costMonitor,
            RemediationDispatcher remediationDispatcher,
            BudgetService budgetService) {
        this.costMonitor = costMonitor;
        this.remediationDispatcher = remediationDispatcher;
        this.budgetService = budgetService;
    }

    @Override
    public int run(String... args) throws Exception {
        costMonitor.detect();
        remediationDispatcher.dispatch("us-east-1", "scale-down-idle-nodes");
        var estimate = budgetService.estimateBlocking("s3", 500);
        System.out.printf("NebulaTrack demo finished; sample estimate for %s: %s%n",
                estimate.service(), estimate.monthlyCost());
        return 0;
    }
}
