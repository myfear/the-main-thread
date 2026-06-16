package dev.windowwatch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class ConversationBudgetRegistryTest {

    private static final int MAX_TOKENS = 120;

    @Test
    void evictsOlderTurnsFromActiveWindow() {
        TokenCountEstimator estimator = new FixedTokenCountEstimator(10);
        ConversationBudgetRegistry registry = new ConversationBudgetRegistry(estimator, MAX_TOKENS, 4096);

        for (int i = 1; i <= 8; i++) {
            String userText = "user turn " + i;
            String assistantText = "assistant turn " + i;
            registry.memory("demo").add(UserMessage.from(userText));
            registry.memory("demo").add(AiMessage.from(assistantText));
            registry.recordTurn("demo", userText, assistantText, new TokenUsage(10, 10, 20));
        }

        var budget = registry.snapshot("demo");

        assertThat(budget.usedTokens()).isLessThanOrEqualTo(MAX_TOKENS);
        assertThat(budget.turns()).hasSize(8);
        assertThat(budget.turns().stream()
                .filter(t -> !t.userActiveInWindow() || !t.assistantActiveInWindow()).count()).isPositive();
        assertThat(budget.turns().get(7).userActiveInWindow()).isTrue();
        assertThat(budget.turns().get(7).assistantActiveInWindow()).isTrue();
        assertThat(budget.evictedMessageTokens()).isPositive();
        assertThat(budget.availableTokens()).isGreaterThanOrEqualTo(0);
        assertThat(budget.retainedTurnTokens()).isPositive();
    }

    @Test
    void canEvictOnlyOneMessageFromATurn() {
        TokenCountEstimator estimator = new FixedTokenCountEstimator(10);
        ConversationBudgetRegistry registry = new ConversationBudgetRegistry(estimator, 75, 4096);

        for (int i = 1; i <= 4; i++) {
            String userText = "user turn " + i;
            String assistantText = "assistant turn " + i;
            registry.memory("partial").add(UserMessage.from(userText));
            registry.memory("partial").add(AiMessage.from(assistantText));
            registry.recordTurn("partial", userText, assistantText, new TokenUsage(10, 10, 20));
        }

        var budget = registry.snapshot("partial");
        var firstTurn = budget.turns().get(0);

        assertThat(firstTurn.userActiveInWindow()).isFalse();
        assertThat(firstTurn.assistantActiveInWindow()).isTrue();
    }

    private static final class FixedTokenCountEstimator implements TokenCountEstimator {

        private final int tokensPerMessage;

        private FixedTokenCountEstimator(int tokensPerMessage) {
            this.tokensPerMessage = tokensPerMessage;
        }

        @Override
        public int estimateTokenCountInText(String text) {
            return tokensPerMessage;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return tokensPerMessage;
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int count = 0;
            for (ChatMessage ignored : messages) {
                count += tokensPerMessage;
            }
            return count;
        }
    }
}
