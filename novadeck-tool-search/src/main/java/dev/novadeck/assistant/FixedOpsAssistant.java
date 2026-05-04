package dev.novadeck.assistant;

import dev.novadeck.tools.audit.AuditTools;
import dev.novadeck.tools.billing.BillingTools;
import dev.novadeck.tools.deploy.DeployTools;
import dev.novadeck.tools.fleet.FleetTools;
import dev.novadeck.tools.incident.IncidentTools;
import dev.novadeck.tools.utility.UtilityTools;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control assistant: full tool catalog visible to the model (no LangChain4j tool search).
 */
@RegisterAiService(
        modelName = "fixed",
        chatMemoryProviderSupplier = NovaDeckChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface FixedOpsAssistant {

    @SystemMessage("""
            You are NovaDeck, an internal operations copilot. Answer with grounded tool calls.
            Prefer tools over guessing. Keep final answers short unless asked for detail.
            """)
    @UserMessage("{prompt}")
    @ToolBox({
            IncidentTools.class,
            DeployTools.class,
            BillingTools.class,
            FleetTools.class,
            AuditTools.class,
            UtilityTools.class
    })
    String ask(String prompt);
}
