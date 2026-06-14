package dev.verdictiq.testsupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.verdictiq.ai.MistralPanelist;
import dev.verdictiq.model.ModelVerdict;
import dev.verdictiq.model.Sentiment;

@Alternative
@ApplicationScoped
public class StubMistralPanelist implements MistralPanelist {

    @Override
    public ModelVerdict classify(String text) {
        if (text.contains("best Java framework")) {
            return new ModelVerdict(Sentiment.POSITIVE, "The language is strongly positive.");
        }
        if (text.contains("airport, which meant we could hear every plane")) {
            return new ModelVerdict(Sentiment.NEGATIVE, "The airport noise makes the overall experience negative.");
        }
        if (text.contains("panel should fail")) {
            return new ModelVerdict(Sentiment.NEGATIVE, "This path should not matter because the other panelist fails first.");
        }
        if (text.contains("judge should fail")) {
            return new ModelVerdict(Sentiment.POSITIVE, "This stub intentionally disagrees to trigger the judge.");
        }
        return new ModelVerdict(Sentiment.NEUTRAL, "Default stub response.");
    }
}
