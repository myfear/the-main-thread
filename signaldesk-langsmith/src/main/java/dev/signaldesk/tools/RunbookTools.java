package dev.signaldesk.tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.Tool;
import dev.signaldesk.trace.AssistTrace;

/**
 * Fake runbook lookup for SignalDesk — deterministic enough for stubs and traces.
 */
@ApplicationScoped
public class RunbookTools {

    private static final Logger LOG = Logger.getLogger(RunbookTools.class);

    public static final String TOOL_NAME = "lookupRunbook";

    @Inject
    AssistTrace assistTrace;

    @Tool("Looks up the on-call runbook for a service and severity. Use for failover or incident response.")
    public String lookupRunbook(String service, String severity) {
        LOG.infof("lookupRunbook service=%s severity=%s", service, severity);

        if (service != null && service.toUpperCase().contains("UNKNOWN-PLAN")) {
            assistTrace.recordToolFailure(TOOL_NAME);
            return "ERROR: runbook not found for service " + service;
        }

        assistTrace.recordTool(TOOL_NAME);
        return "runbook-" + service + "-" + severity + ": page platform-oncall, open status channel, follow failover checklist RB-12";
    }
}
