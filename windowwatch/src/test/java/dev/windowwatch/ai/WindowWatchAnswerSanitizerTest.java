package dev.windowwatch.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WindowWatchAnswerSanitizerTest {

    @Test
    void stripsRedactedThinkingSuffix() {
        String raw = "long reasoning</think>\n\nhello";
        assertThat(WindowWatchAnswerSanitizer.visibleAnswer(raw)).isEqualTo("hello");
    }
}
