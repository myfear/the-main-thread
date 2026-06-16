package dev.windowwatch.budget;

import java.util.List;

public record ConversationBudget(
        String memoryId,
        int usedTokens,
        int maxTokens,
        double fillRatio,
        String state,
        List<BudgetTurn> turns,
        int retainedTurnTokens,
        int evictedMessageTokens,
        int otherRetainedTokens,
        int availableTokens,
        Integer lastRequestInputTokens,
        Integer lastRequestOutputTokens,
        int configuredModelMaxTokens) {
}
