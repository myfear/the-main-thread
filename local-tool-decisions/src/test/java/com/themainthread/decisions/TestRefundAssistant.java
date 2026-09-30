package com.themainthread.decisions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/** A real Quarkus AI Service with a deterministic, test-only chat model. */
@RegisterAiService(chatLanguageModelSupplier = TestRefundAssistant.ModelSupplier.class,
        tools = GuardedOrderTools.class)
public interface TestRefundAssistant {
    String chat(@MemoryId String memoryId, @UserMessage String userRequest, InvocationParameters parameters);

    class ModelSupplier implements Supplier<ChatModel> {
        private static final AtomicInteger CALLS = new AtomicInteger();

        static void reset() {
            CALLS.set(0);
        }

        static int calls() {
            return CALLS.get();
        }

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    CALLS.incrementAndGet();
                    boolean toolFinished = request.messages().getLast() instanceof ToolExecutionResultMessage;
                    AiMessage message = toolFinished ? AiMessage.from("Refund tool completed.")
                            : AiMessage.from(ToolExecutionRequest.builder()
                                    .id("refund-4712")
                                    .name("issueRefund")
                                    .arguments("{\"orderId\":\"4712\",\"amountCents\":1999}")
                                    .build());
                    return ChatResponse.builder()
                            .aiMessage(message)
                            .modelName("scripted-test-chat")
                            .tokenUsage(new TokenUsage(12, 3))
                            .finishReason(toolFinished ? FinishReason.STOP : FinishReason.TOOL_EXECUTION)
                            .build();
                }
            };
        }
    }
}
