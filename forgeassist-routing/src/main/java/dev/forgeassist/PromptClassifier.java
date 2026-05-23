package dev.forgeassist;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "fast")
public interface PromptClassifier {

    @SystemMessage("""
            You are a prompt complexity classifier for ForgeCI, a CI/CD platform.
            Classify the user's question into exactly one of: SIMPLE, COMPLEX.

            SIMPLE: factual lookups, flag definitions, single-step how-tos,
                    questions answerable from documentation alone.

            COMPLEX: debugging with environment context, multi-step reasoning,
                     architectural trade-offs, questions that require inference
                     beyond what documentation states.

            Respond with ONLY the enum value. No punctuation. No explanation.
            """)
    Complexity classify(@UserMessage String prompt);
}