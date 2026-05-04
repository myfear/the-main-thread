package dev.topology.testsupport;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Deterministic model for {@code @QuarkusTest} so the agentic graph runs without Ollama.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TopologyStubChatModel implements ChatModel {

    @Override
    public ChatResponse chat(ChatRequest request) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("stub specialist output"))
                .build();
    }
}
