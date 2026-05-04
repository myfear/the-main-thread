package dev.novadeck.assistant;

import java.util.function.Supplier;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * Supplies chat memory for multi-step tool loops in declarative AI services.
 */
public class NovaDeckChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

    @Override
    public ChatMemoryProvider get() {
        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.withMaxMessages(40);
            }
        };
    }
}
