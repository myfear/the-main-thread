package dev.windowwatch.testsupport;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@ApplicationScoped
public class TestTokenCountEstimator implements TokenCountEstimator {

    private static final int TOKENS_PER_MESSAGE = 10;

    @Override
    public int estimateTokenCountInText(String text) {
        return TOKENS_PER_MESSAGE;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return TOKENS_PER_MESSAGE;
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage ignored : messages) {
            count += TOKENS_PER_MESSAGE;
        }
        return count;
    }
}
