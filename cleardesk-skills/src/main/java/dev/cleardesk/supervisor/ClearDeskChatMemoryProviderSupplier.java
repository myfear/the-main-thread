package dev.cleardesk.supervisor;

import java.util.function.Supplier;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * Memory for multi-step tool loops (activate_skill, then route, then optional specialist tools).
 */
public class ClearDeskChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

    @Override
    public ChatMemoryProvider get() {
        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.withMaxMessages(30);
            }
        };
    }
}
