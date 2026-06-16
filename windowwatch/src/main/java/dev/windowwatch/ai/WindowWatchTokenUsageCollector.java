package dev.windowwatch.ai;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WindowWatchTokenUsageCollector implements ChatModelListener {

    @Inject
    Instance<WindowWatchRequestUsage> requestUsage;

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null && requestUsage.isResolvable()) {
            requestUsage.get().capture(tokenUsage);
        }
    }
}
