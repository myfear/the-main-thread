package dev.cleardesk.supervisor;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Same delegate tools as {@link SupervisorWithSkillsAssistant}, but no Skills tool provider — routing relies on a vague
 * org chart instead of filesystem contracts.
 */
@RegisterAiService(
        chatMemoryProviderSupplier = ClearDeskChatMemoryProviderSupplier.class,
        toolProviderSupplier = RegisterAiService.NoToolProviderSupplier.class)
@ApplicationScoped
public interface SupervisorBaselineAssistant {

    @SystemMessage(
            """
                    You are ClearDesk supervisor. You must call exactly one routing tool: routeToSupport, routeToFinance, or routeToDevOps.
                    Vague org chart: Alex is support, Sam is finance, Jordan is devops. When the user message is ambiguous, guess from loose keywords
                    (for example: "API" and "production" could sound like devops even if the issue is about checkout money).
                    """)
    @UserMessage("{{prompt}}")
    @ToolBox(SupervisorDelegateTools.class)
    String handle(@MemoryId String memoryId, String prompt);
}
