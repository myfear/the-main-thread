package dev.windowwatch.ai;

import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class WindowWatchRequestUsage {

    private TokenUsage tokenUsage;

    public void capture(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }
}
