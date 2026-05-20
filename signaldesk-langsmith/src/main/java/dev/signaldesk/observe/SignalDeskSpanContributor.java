package dev.signaldesk.observe;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;

/**
 * Adds app-specific metadata on top of standard {@code gen_ai.*} span attributes.
 */
@ApplicationScoped
public class SignalDeskSpanContributor implements ChatModelSpanContributor {

    private static final String WORKFLOW = "signaldesk-assist";

    @Override
    public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
        currentSpan.setAttribute("signaldesk.workflow", WORKFLOW);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
        currentSpan.setAttribute("signaldesk.workflow", WORKFLOW);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext, Span currentSpan) {
        currentSpan.setAttribute("signaldesk.workflow", WORKFLOW);
    }
}
