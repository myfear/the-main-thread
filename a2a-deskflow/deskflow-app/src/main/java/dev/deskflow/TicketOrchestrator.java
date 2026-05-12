package dev.deskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deskflow.model.SupportTicket;
import dev.deskflow.model.TriageResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TicketOrchestrator {

    private static final Logger LOG = Logger.getLogger(TicketOrchestrator.class);

    private final TriageAgent triageAgent;
    private final KnowledgeBaseClient kbClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public TicketOrchestrator(TriageAgent triageAgent, KnowledgeBaseClient kbClient) {
        this.triageAgent = triageAgent;
        this.kbClient = kbClient;
    }

    public TriageResult process(SupportTicket ticket) {
        LOG.infof("[Orchestrator] Processing ticket %s: %s", ticket.id(), ticket.summary());

        String triageJson = triageAgent.classify(ticket.summary(), ticket.details());
        LOG.debugf("[Orchestrator] Triage result JSON: %s", triageJson);

        String severity;
        String category;
        boolean escalation;
        try {
            JsonNode node = mapper.readTree(triageJson);
            severity = node.get("severity").asText();
            category = node.get("category").asText();
            escalation = node.get("escalationRequired").asBoolean();
        } catch (Exception e) {
            LOG.errorf(e, "[Orchestrator] Failed to parse triage JSON, defaulting to MEDIUM/OTHER");
            severity = "MEDIUM";
            category = "OTHER";
            escalation = false;
        }

        category = sanitizeCategory(category);

        LOG.infof("[Orchestrator] Calling remote KB agent — category=%s severity=%s", category, severity);
        String remediation = kbClient.findRemediation(category, severity, ticket.summary(), ticket.details());

        return new TriageResult(ticket.id(), severity, category, remediation, escalation);
    }

    /**
     * Models sometimes emit multiple enum labels (e.g. {@code "PERFORMANCE|SOFTWARE"}). The KB contract expects a
     * single category token.
     */
    private static String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }
        String first = category.split("\\|")[0].trim();
        return first.isEmpty() ? "OTHER" : first;
    }
}
