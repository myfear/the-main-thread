package dev.windowwatch.testsupport;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.windowwatch.ai.ConversationBudgetRegistry;
import dev.windowwatch.ai.WindowWatchAssistant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

@Alternative
@ApplicationScoped
public class StubWindowWatchAssistant implements WindowWatchAssistant {

    @Inject
    ConversationBudgetRegistry budgets;

    @Override
    public String chat(String memoryId, String prompt) {
        String answer = "Stub answer for: " + prompt;
        budgets.memory(memoryId).add(UserMessage.from(prompt));
        budgets.memory(memoryId).add(AiMessage.from(answer));
        return answer;
    }
}
