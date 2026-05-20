package dev.signaldesk.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.signaldesk.tools.RunbookTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface SignalDeskAssistant {

    @SystemMessage(
            """
            You are SignalDesk, an internal support assistant for on-call engineers.
            Answer SLA and policy questions directly when no runbook lookup is needed.
            For SEV-1 failover or explicit runbook requests, call lookupRunbook with service and severity.
            Keep answers short.""")
    @UserMessage("{{question}}")
    @ToolBox(RunbookTools.class)
    String assist(String question);
}
