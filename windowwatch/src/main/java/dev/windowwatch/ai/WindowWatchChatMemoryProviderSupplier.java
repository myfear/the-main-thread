package dev.windowwatch.ai;

import java.util.function.Supplier;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import jakarta.enterprise.inject.spi.CDI;

public class WindowWatchChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> CDI.current()
                .select(ConversationBudgetRegistry.class)
                .get()
                .memory(memoryId.toString());
    }
}
