package dev.signaldesk.testsupport;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.signaldesk.tools.RunbookTools;

/**
 * Deterministic {@link ChatModel} for tests via {@link SignalDeskStubProfile}.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class SignalDeskStubChatModel implements ChatModel {

    public static final String PLAIN_PROMPT = "What is our SLA for SEV-2?";
    public static final String TOOL_PROMPT = "SEV-1 database failover — which runbook?";
    public static final String FAILURE_PROMPT = "Trigger runbook lookup for UNKNOWN-PLAN";

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();

        if (toolRoundCompleted(messages)) {
            String lastResult = lastToolResult(messages);
            if (lastResult != null && lastResult.toUpperCase().contains("ERROR")) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Stub: runbook lookup failed — " + lastResult))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Stub: runbook steps recorded. Escalate if failover stalls."))
                    .build();
        }

        String userText = lastUserText(messages);
        if (userText == null) {
            return ChatResponse.builder().aiMessage(AiMessage.from("Stub: no question received.")).build();
        }

        if (userText.contains("UNKNOWN-PLAN")) {
            return respondTool(
                    RunbookTools.TOOL_NAME,
                    "{\"service\":\"UNKNOWN-PLAN\",\"severity\":\"SEV-1\"}");
        }

        if (userText.contains("failover") || userText.contains("runbook")) {
            return respondTool(
                    RunbookTools.TOOL_NAME,
                    "{\"service\":\"database\",\"severity\":\"SEV-1\"}");
        }

        return ChatResponse.builder()
                .aiMessage(
                        AiMessage.from(
                                "Stub: SEV-2 incidents get a 30-minute initial response and 4-hour mitigation target."))
                .build();
    }

    private static boolean toolRoundCompleted(List<ChatMessage> messages) {
        return messages.stream()
                .anyMatch(m -> m instanceof ToolExecutionResultMessage tr && RunbookTools.TOOL_NAME.equals(tr.toolName()));
    }

    private static String lastToolResult(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m instanceof ToolExecutionResultMessage tr && RunbookTools.TOOL_NAME.equals(tr.toolName())) {
                return tr.text();
            }
        }
        return null;
    }

    private static String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.singleText();
            }
        }
        return null;
    }

    private static ChatResponse respondTool(String name, String arguments) {
        ToolExecutionRequest req =
                ToolExecutionRequest.builder().id("stub-id").name(name).arguments(arguments).build();
        return ChatResponse.builder().aiMessage(AiMessage.from(req)).build();
    }
}
