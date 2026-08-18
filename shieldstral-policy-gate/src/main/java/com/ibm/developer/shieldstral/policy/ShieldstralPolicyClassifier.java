package com.ibm.developer.shieldstral.policy;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.ibm.developer.shieldstral.config.ShieldstralConfig;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.LogProb;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;

@ApplicationScoped
final class ShieldstralPolicyClassifier implements PolicyClassifier {

    static final String SYSTEM_PROMPT = "Judge whether the Document meets the requirements based on the Query "
            + "and the Instruction provided. Note that the answer can only be \"yes\" or \"no\".";

    private static final Set<String> YES_TOKENS = Set.of("yes", "yes.", "\"yes\"", "'yes'");
    private static final Set<String> NO_TOKENS = Set.of("no", "no.", "\"no\"", "'no'");
    private static final double MISSING_LOG_PROBABILITY = -10.0;

    private final ChatModel model;

    ShieldstralPolicyClassifier(ShieldstralConfig config) {
        model = OpenAiChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .temperature(0.0)
                .maxTokens(1)
                .logprobs(true)
                .topLogprobs(20)
                .timeout(config.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Override
    public ClassifierScore classify(ClassifierRequest request) {
        ChatResponse response = model.chat(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(request.userMessage()));

        if (!(response.metadata() instanceof OpenAiChatResponseMetadata metadata)) {
            throw new PolicyClassifierException("Shieldstral did not return OpenAI response metadata");
        }

        List<LogProb> positions = metadata.logProbs();
        if (positions == null || positions.isEmpty() || positions.getFirst().topLogprobs() == null) {
            throw new PolicyClassifierException("Shieldstral did not return first-token log probabilities");
        }

        return new ClassifierScore(unsafeScore(positions.getFirst().topLogprobs()));
    }

    static double unsafeScore(List<LogProb> topLogProbabilities) {
        double yesLogProbability = MISSING_LOG_PROBABILITY;
        double noLogProbability = MISSING_LOG_PROBABILITY;
        boolean foundAnswerClass = false;

        for (LogProb candidate : topLogProbabilities) {
            String token = candidate.token().strip().toLowerCase(Locale.ROOT);
            if (YES_TOKENS.contains(token)) {
                yesLogProbability = Math.max(yesLogProbability, candidate.logprob());
                foundAnswerClass = true;
            } else if (NO_TOKENS.contains(token)) {
                noLogProbability = Math.max(noLogProbability, candidate.logprob());
                foundAnswerClass = true;
            }
        }

        if (!foundAnswerClass) {
            throw new PolicyClassifierException("Shieldstral did not return a yes or no token probability");
        }

        double largest = Math.max(yesLogProbability, noLogProbability);
        double yesWeight = Math.exp(yesLogProbability - largest);
        double noWeight = Math.exp(noLogProbability - largest);
        return yesWeight / (yesWeight + noWeight);
    }
}
