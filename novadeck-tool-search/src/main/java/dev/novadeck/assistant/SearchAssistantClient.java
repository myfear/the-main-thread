package dev.novadeck.assistant;

import dev.novadeck.tools.audit.AuditTools;
import dev.novadeck.tools.billing.BillingTools;
import dev.novadeck.tools.deploy.DeployTools;
import dev.novadeck.tools.fleet.FleetTools;
import dev.novadeck.tools.incident.IncidentTools;
import dev.novadeck.tools.utility.UtilityTools;
import dev.novadeck.trace.ToolSearchTraceRegistry;
import dev.novadeck.trace.TracingToolSearchStrategy;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import io.quarkiverse.langchain4j.ModelName;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Builds the tool-search assistant with {@link AiServices} (same Ollama model name as {@link FixedOpsAssistant}).
 */
@ApplicationScoped
public class SearchAssistantClient {

    private static final Logger LOG = Logger.getLogger(SearchAssistantClient.class);

    private final SearchOpsAssistant delegate;

    @Inject
    public SearchAssistantClient(
            @ModelName("search") ChatModel chatModel,
            IncidentTools incidentTools,
            DeployTools deployTools,
            BillingTools billingTools,
            FleetTools fleetTools,
            AuditTools auditTools,
            UtilityTools utilityTools,
            ToolSearchTraceRegistry traceRegistry) {
        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(40);
        SimpleToolSearchStrategy inner = SimpleToolSearchStrategy.builder()
                .maxResults(12)
                .build();
        TracingToolSearchStrategy tracing = new TracingToolSearchStrategy(inner, traceRegistry);
        this.delegate = AiServices.builder(SearchOpsAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .tools(incidentTools, deployTools, billingTools, fleetTools, auditTools, utilityTools)
                .toolSearchStrategy(tracing)
                .maxSequentialToolsInvocations(24)
                .build();
        LOG.info("NovaDeck SearchOpsAssistant ready (LangChain4j Tool Search enabled)");
    }

    public String ask(String prompt) {
        return delegate.ask(prompt);
    }
}
