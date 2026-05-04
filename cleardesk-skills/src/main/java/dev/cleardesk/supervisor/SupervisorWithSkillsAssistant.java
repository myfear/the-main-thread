package dev.cleardesk.supervisor;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Supervisor with LangChain4j Skills in tool mode (plus delegate tools). Quarkus only supports skills as tools today.
 */
@RegisterAiService(
        chatMemoryProviderSupplier = ClearDeskChatMemoryProviderSupplier.class,
        systemMessageProviderSupplier = ClearDeskSkillsSystemMessageProvider.class)
@ApplicationScoped
public interface SupervisorWithSkillsAssistant {

    @UserMessage("{{prompt}}")
    @ToolBox(SupervisorDelegateTools.class)
    String handle(@MemoryId String memoryId, String prompt);
}
