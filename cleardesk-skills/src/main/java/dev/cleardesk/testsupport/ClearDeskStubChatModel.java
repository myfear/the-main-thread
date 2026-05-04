package dev.cleardesk.testsupport;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Deterministic {@link ChatModel} enabled for tests via {@code %test.quarkus.arc.selected-alternatives}.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class ClearDeskStubChatModel implements ChatModel {

    public static final String AMBIGUOUS_PROMPT =
            "Production checkout API returns 500 when applying the corporate VAT invoice; money must flow before the weekend deploy.";

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();

        boolean delegateCompleted = messages.stream()
                .anyMatch(m -> m instanceof ToolExecutionResultMessage tr
                        && (tr.toolName().equals("routeToSupport")
                                || tr.toolName().equals("routeToFinance")
                                || tr.toolName().equals("routeToDevOps")));
        if (delegateCompleted) {
            return ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("stub routing complete"))
                    .build();
        }

        String userText = lastUserText(messages);
        String systemText = lastSystemText(messages);
        boolean baseline = systemText != null && systemText.contains("Vague org chart");
        boolean skills = systemText != null && systemText.contains("You are ClearDesk, an internal supervisor");

        if (userText != null && userText.contains("invoice refund for ticket INC-9")) {
            return respondTool("routeToFinance", "{\"reason\":\"Finance owns refunds linked to tickets.\"}");
        }
        if (userText != null && userText.contains("pipeline release-42 keeps failing on the build agent")) {
            return respondTool("routeToDevOps", "{\"reason\":\"CI failures go to platform.\"}");
        }
        if (userText != null && userText.contains("customer cannot authenticate; open SEV-2 ticket")) {
            return respondTool("routeToSupport", "{\"reason\":\"Login outage is support intake.\"}");
        }

        if (userText != null && userText.contains("checkout API") && userText.contains("VAT invoice")) {
            if (baseline) {
                return respondTool("routeToDevOps", "{\"reason\":\"Sounds like production API — baseline chooses devops.\"}");
            }
            if (skills) {
                if (!hasToolResult(messages, "activate_skill")) {
                    return respondTool(
                            "activate_skill",
                            "{\"skill_name\":\"finance-ops\"}",
                            "1");
                }
                return respondTool("routeToFinance", "{\"reason\":\"invoice/VAT path\"}", "2");
            }
        }

        return respondTool("routeToSupport", "{\"reason\":\"default\"}");
    }

    private static boolean hasToolResult(List<ChatMessage> messages, String toolName) {
        for (ChatMessage m : messages) {
            if (m instanceof ToolExecutionResultMessage tr && toolName.equals(tr.toolName())) {
                return true;
            }
        }
        return false;
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

    private static String lastSystemText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m instanceof SystemMessage sm) {
                return sm.text();
            }
        }
        return null;
    }

    private static ChatResponse respondTool(String name, String arguments) {
        return respondTool(name, arguments, "stub-id");
    }

    private static ChatResponse respondTool(String name, String arguments, String id) {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
        return ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from(req)).build();
    }
}
