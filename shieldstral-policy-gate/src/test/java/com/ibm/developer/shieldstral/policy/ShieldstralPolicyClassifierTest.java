package com.ibm.developer.shieldstral.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.LogProb;

class ShieldstralPolicyClassifierTest {

    @Test
    void renormalizesYesAndNoLogProbabilities() {
        List<LogProb> topLogProbabilities = List.of(
                token("yes", Math.log(0.8)),
                token("no", Math.log(0.2)),
                token("maybe", Math.log(0.7)));

        double score = ShieldstralPolicyClassifier.unsafeScore(topLogProbabilities);

        assertEquals(0.8, score, 0.000_001);
    }

    @Test
    void acceptsQuotedAndPunctuatedAnswerTokens() {
        List<LogProb> topLogProbabilities = List.of(
                token(" \"yes\"", Math.log(0.1)),
                token(" No.", Math.log(0.9)));

        double score = ShieldstralPolicyClassifier.unsafeScore(topLogProbabilities);

        assertEquals(0.1, score, 0.000_001);
    }

    @Test
    void rejectsTopProbabilitiesWithoutAnAnswerClass() {
        List<LogProb> topLogProbabilities = List.of(token("maybe", Math.log(0.9)));

        assertThrows(
                PolicyClassifierException.class,
                () -> ShieldstralPolicyClassifier.unsafeScore(topLogProbabilities));
    }

    private static LogProb token(String token, double logProbability) {
        return LogProb.builder()
                .token(token)
                .logprob(logProbability)
                .topLogprobs(List.of())
                .build();
    }
}
