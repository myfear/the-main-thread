package dev.windowwatch.budget;

public record BudgetTurn(
        int turn,
        String userText,
        int userTokens,
        boolean userActiveInWindow,
        String assistantText,
        int assistantTokens,
        boolean assistantActiveInWindow) {
}
