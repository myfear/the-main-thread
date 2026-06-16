package dev.windowwatch.ai;

public final class WindowWatchAnswerSanitizer {

    private static final String REDACTED_THINKING_END = "</think>";
    private static final String THINK_END = "<" + "/think>";

    private WindowWatchAnswerSanitizer() {
    }

    public static String visibleAnswer(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        int redactedEnd = raw.lastIndexOf(REDACTED_THINKING_END);
        if (redactedEnd >= 0) {
            return raw.substring(redactedEnd + REDACTED_THINKING_END.length()).strip();
        }
        int thinkEnd = raw.lastIndexOf(THINK_END);
        if (thinkEnd >= 0) {
            return raw.substring(thinkEnd + THINK_END.length()).strip();
        }
        return raw.strip();
    }
}
