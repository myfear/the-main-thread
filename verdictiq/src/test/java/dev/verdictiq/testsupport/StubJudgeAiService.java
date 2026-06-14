package dev.verdictiq.testsupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.verdictiq.ai.JudgeAiService;
import dev.verdictiq.model.ModelVerdict;
import dev.verdictiq.model.Sentiment;

@Alternative
@ApplicationScoped
public class StubJudgeAiService implements JudgeAiService {

    @Override
    public ModelVerdict adjudicate(String text, String labelA, String reasonA, String labelB, String reasonB) {
        if (text.contains("judge should fail")) {
            throw new IllegalStateException("Stub judge failed on purpose.");
        }
        if (text.contains("airport, which meant we could hear every plane")) {
            return new ModelVerdict(Sentiment.UNCERTAIN, "The text contains a real trade-off and stays ambiguous.");
        }
        return new ModelVerdict(Sentiment.NEUTRAL, "Default stub verdict.");
    }
}
