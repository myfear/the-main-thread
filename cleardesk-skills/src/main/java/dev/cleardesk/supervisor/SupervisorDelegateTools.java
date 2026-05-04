package dev.cleardesk.supervisor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.cleardesk.routing.RoutingTrace;
import dev.cleardesk.routing.Specialist;
import dev.cleardesk.specialists.DevOpsSpecialistTools;
import dev.cleardesk.specialists.FinanceSpecialistTools;
import dev.cleardesk.specialists.SupportSpecialistTools;
import dev.langchain4j.agent.tool.Tool;

/**
 * Supervisor-only routing tools. The model must pick exactly one of these after it has decided
 * which specialist owns the work (optionally after activating a filesystem skill).
 */
@ApplicationScoped
public class SupervisorDelegateTools {

    private static final Logger LOG = Logger.getLogger(SupervisorDelegateTools.class);

    @Inject
    RoutingTrace routingTrace;

    @Inject
    SupportSpecialistTools supportSpecialistTools;

    @Inject
    FinanceSpecialistTools financeSpecialistTools;

    @Inject
    DevOpsSpecialistTools devOpsSpecialistTools;

    @Tool("Routes the request to Support (tickets, customer-visible incidents, SLAs).")
    public String routeToSupport(String reason) {
        LOG.infof("route support: %s", reason);
        routingTrace.record(Specialist.SUPPORT);
        return supportSpecialistTools.recordSupportIntake(reason);
    }

    @Tool("Routes the request to Finance (refunds, invoices, billing, payment capture).")
    public String routeToFinance(String reason) {
        LOG.infof("route finance: %s", reason);
        routingTrace.record(Specialist.FINANCE);
        return financeSpecialistTools.lookupInvoice("auto-from:" + reason);
    }

    @Tool("Routes the request to DevOps (CI/CD, deploys, clusters, build pipelines).")
    public String routeToDevOps(String reason) {
        LOG.infof("route devops: %s", reason);
        routingTrace.record(Specialist.DEVOPS);
        return devOpsSpecialistTools.checkPipeline("auto-from:" + reason);
    }
}
