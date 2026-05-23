package dev.forgeassist;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class ModelRouter {

    private final PromptClassifier classifier;
    private final ChatModel fastModel;
    private final ChatModel powerModel;
    private final Event<RoutingDecision> routingEvents;

    @Inject
    public ModelRouter(
            PromptClassifier classifier,
            @ModelName("fast") ChatModel fastModel,
            ChatModel powerModel,
            Event<RoutingDecision> routingEvents) {
        this.classifier = classifier;
        this.fastModel = fastModel;
        this.powerModel = powerModel;
        this.routingEvents = routingEvents;
    }

    public String route(String userPrompt) {
        long start = System.currentTimeMillis();
        Complexity complexity = classifier.classify(userPrompt);
        long classificationMillis = System.currentTimeMillis() - start;

        ChatModel selected = switch (complexity) {
            case SIMPLE -> fastModel;
            case COMPLEX -> powerModel;
        };

        ChatRequest request = ChatRequest.builder().messages(UserMessage.from(userPrompt)).build();

        String response = selected.chat(request).aiMessage().text();

        routingEvents.fireAsync(
                new RoutingDecision(
                        userPrompt,
                        complexity,
                        complexity == Complexity.SIMPLE ? "qwen2.5:0.5b" : "llama3.2",
                        classificationMillis));

        return response;
    }
}