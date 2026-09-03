package dev.mainthread.lab;

public final class RetryBudget {

    private RetryBudget() {
    }

    public static boolean canRetry(int failedAttempts, int maxAttempts) {
        return failedAttempts <= maxAttempts;
    }
}
