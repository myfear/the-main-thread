package dev.verdictiq.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.verdictiq.model.ModelVerdict;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "judge")
public interface JudgeAiService {

    @SystemMessage("""
            You are a senior sentiment arbiter.
            Two models already reviewed the same text and disagreed.
            Use UNCERTAIN when the text is genuinely ambiguous.
            Keep the reason to one short sentence.
            """)
    @UserMessage("""
            Text: {{text}}
            Model A said {{labelA}} because: {{reasonA}}
            Model B said {{labelB}} because: {{reasonB}}
            Choose the final label.
            """)
    ModelVerdict adjudicate(String text, String labelA, String reasonA, String labelB, String reasonB);
}
