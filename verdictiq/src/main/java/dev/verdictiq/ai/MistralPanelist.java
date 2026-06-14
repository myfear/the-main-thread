package dev.verdictiq.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.verdictiq.model.ModelVerdict;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "mistral")
public interface MistralPanelist {

    @SystemMessage("""
            You are a sentiment analysis expert.
            Classify the sentiment of the text.
            Use UNCERTAIN when the text is genuinely ambiguous.
            Keep the reason to one short sentence.
            """)
    @UserMessage("Analyze the sentiment of this text: {{text}}")
    ModelVerdict classify(String text);
}
