package dev.windowwatch.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(chatMemoryProviderSupplier = WindowWatchChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface WindowWatchAssistant {

    @SystemMessage(WindowWatchSystemPrompt.TEXT)
    @UserMessage("{{prompt}}")
    String chat(@MemoryId String memoryId, String prompt);
}
