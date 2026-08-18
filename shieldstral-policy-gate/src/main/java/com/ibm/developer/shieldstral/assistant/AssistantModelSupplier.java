package com.ibm.developer.shieldstral.assistant;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import com.ibm.developer.shieldstral.config.AssistantModelConfig;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;

@ApplicationScoped
public final class AssistantModelSupplier implements Supplier<ChatModel> {

    private final ChatModel model;

    AssistantModelSupplier(AssistantModelConfig config) {
        model = MistralAiChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .temperature(0.2)
                .maxTokens(300)
                .timeout(config.timeout())
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Override
    public ChatModel get() {
        return model;
    }
}
