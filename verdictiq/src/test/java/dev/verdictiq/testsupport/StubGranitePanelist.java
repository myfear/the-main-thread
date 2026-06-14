package dev.verdictiq.testsupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import dev.verdictiq.ai.GranitePanelist;
import dev.verdictiq.model.ModelVerdict;
import dev.verdictiq.model.Sentiment;

@Alternative
@ApplicationScoped
public class StubGranitePanelist implements GranitePanelist {

    @Override
    public ModelVerdict classify(String text) {
        if (text.contains("best Java framework")) {
            return new ModelVerdict(Sentiment.POSITIVE, "The text is clearly enthusiastic.");
        }
        if (text.contains("airport, which meant we could hear every plane")) {
            return new ModelVerdict(Sentiment.NEUTRAL, "The statement mixes convenience and annoyance.");
        }
        if (text.contains("panel should fail")) {
            throw new IllegalStateException("Stub panel failed on purpose.");
        }
        if (text.contains("judge should fail")) {
            return new ModelVerdict(Sentiment.NEGATIVE, "The wording is clearly negative.");
        }
        return new ModelVerdict(Sentiment.NEUTRAL, "Default stub response.");
    }
}
