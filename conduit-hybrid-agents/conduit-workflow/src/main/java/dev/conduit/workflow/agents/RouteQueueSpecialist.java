package dev.conduit.workflow.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.CreatedAware;

@CreatedAware
public interface RouteQueueSpecialist {

    @Agent
    @UserMessage("Return exactly one queue token: ops-general, ops-priority, or ops-security. Severity: {{severity_label}}. Summary: {{handoff_summary}}")
    String route(
            @V("severity_label") String severityLabel,
            @V("handoff_summary") String handoffSummary);
}