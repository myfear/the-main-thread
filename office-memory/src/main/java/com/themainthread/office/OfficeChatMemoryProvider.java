package com.themainthread.office;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

@ApplicationScoped
public class OfficeChatMemoryProvider implements ChatMemoryProvider {
    private final HibernateChatMemoryStore store;

    public OfficeChatMemoryProvider(HibernateChatMemoryStore store) {
        this.store = store;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return MessageWindowChatMemory.builder().id(memoryId).maxMessages(12).chatMemoryStore(store).build();
    }
}
