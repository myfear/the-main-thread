package dev.windowwatch.http;

import dev.langchain4j.model.output.TokenUsage;
import dev.windowwatch.ai.ConversationBudgetRegistry;
import dev.windowwatch.ai.WindowWatchAssistant;
import dev.windowwatch.ai.WindowWatchAnswerSanitizer;
import dev.windowwatch.ai.WindowWatchRequestUsage;
import dev.windowwatch.budget.ChatTurnResponse;
import dev.windowwatch.budget.ConversationBudget;
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WindowWatchResource {

    @Inject
    WindowWatchAssistant assistant;

    @Inject
    ConversationBudgetRegistry budgets;

    @Inject
    WindowWatchRequestUsage requestUsage;

    @POST
    @Path("/chat/{memoryId}")
    public ChatTurnResponse chat(@PathParam("memoryId") String memoryId, PromptRequest request) {
        String answer = WindowWatchAnswerSanitizer.visibleAnswer(assistant.chat(memoryId, request.prompt()));
        TokenUsage usage = requestUsage.tokenUsage();
        budgets.recordTurn(memoryId, request.prompt(), answer, usage);
        ConversationBudget budget = budgets.snapshot(memoryId);
        tagCurrentSpan(memoryId, budget, usage);
        return new ChatTurnResponse(answer, budget);
    }

    @GET
    @Path("/budget/{memoryId}")
    public ConversationBudget budget(@PathParam("memoryId") String memoryId) {
        return budgets.snapshot(memoryId);
    }

    private void tagCurrentSpan(String memoryId, ConversationBudget budget, TokenUsage usage) {
        Span span = Span.current();
        span.setAttribute("windowwatch.memory.id", memoryId);
        span.setAttribute("windowwatch.budget.used_tokens", budget.usedTokens());
        span.setAttribute("windowwatch.budget.max_tokens", budget.maxTokens());
        span.setAttribute("windowwatch.budget.fill_ratio", budget.fillRatio());
        span.setAttribute("windowwatch.budget.state", budget.state());
        span.setAttribute("windowwatch.budget.retained_turn_tokens", budget.retainedTurnTokens());
        span.setAttribute("windowwatch.budget.evicted_message_tokens", budget.evictedMessageTokens());
        span.setAttribute("windowwatch.budget.other_retained_tokens", budget.otherRetainedTokens());
        span.setAttribute("windowwatch.budget.available_tokens", budget.availableTokens());
        span.setAttribute("windowwatch.request.configured_model_max_tokens", budget.configuredModelMaxTokens());
        if (usage != null) {
            if (usage.inputTokenCount() != null) {
                span.setAttribute("windowwatch.request.input_tokens", usage.inputTokenCount());
            }
            if (usage.outputTokenCount() != null) {
                span.setAttribute("windowwatch.request.output_tokens", usage.outputTokenCount());
            }
            if (usage.totalTokenCount() != null) {
                span.setAttribute("windowwatch.request.total_tokens", usage.totalTokenCount());
            }
        }
    }
}
