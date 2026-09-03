package dev.mainthread.lab;

public final class RetryBudgetCheck {

    private RetryBudgetCheck() {
    }

    public static void main(String[] args) {
        expect(RetryBudget.canRetry(0, 3), "the initial call is available");
        expect(RetryBudget.canRetry(2, 3), "the last retry is available");
        expect(!RetryBudget.canRetry(3, 3), "the budget is exhausted after three failures");
        expectIllegalArgument(() -> RetryBudget.canRetry(-1, 3), "negative failures");
        expectIllegalArgument(() -> RetryBudget.canRetry(0, 0), "zero max attempts");
        System.out.println("RetryBudget contract verified");
    }

    private static void expect(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void expectIllegalArgument(Runnable operation, String description) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException for " + description);
        } catch (IllegalArgumentException expected) {
            // Expected by the contract.
        }
    }
}
