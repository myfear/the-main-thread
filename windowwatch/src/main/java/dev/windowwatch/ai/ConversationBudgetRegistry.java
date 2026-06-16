package dev.windowwatch.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import dev.windowwatch.budget.BudgetTurn;
import dev.windowwatch.budget.ConversationBudget;
import dev.windowwatch.config.WindowWatchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConversationBudgetRegistry {

    private final TokenCountEstimator estimator;
    private final int maxWindowTokens;
    private final int configuredModelMaxTokens;
    private final ConcurrentMap<String, ConversationState> states = new ConcurrentHashMap<>();

    @Inject
    ConversationBudgetRegistry(TokenCountEstimator estimator, WindowWatchConfig config) {
        this(
                estimator,
                config.budget().maxTokens(),
                config.budget().modelContextTokens());
    }

    ConversationBudgetRegistry(
            TokenCountEstimator estimator,
            int maxWindowTokens,
            int configuredModelMaxTokens) {
        this.estimator = estimator;
        this.maxWindowTokens = maxWindowTokens;
        this.configuredModelMaxTokens = configuredModelMaxTokens;
    }

    public ChatMemory memory(String memoryId) {
        return state(memoryId).memory();
    }

    public void recordTurn(String memoryId, String prompt, String assistantText, TokenUsage usage) {
        state(memoryId).recordTurn(prompt, assistantText, usage);
    }

    public ConversationBudget snapshot(String memoryId) {
        return state(memoryId).snapshot();
    }

    private ConversationState state(String memoryId) {
        return states.computeIfAbsent(memoryId, id -> new ConversationState(id, estimator, maxWindowTokens));
    }

    private final class ConversationState {

        private final String memoryId;
        private final TokenCountEstimator estimator;
        private final int maxTokens;
        private final TokenWindowChatMemory memory;
        private final List<RecordedTurn> turns = new ArrayList<>();

        private ConversationState(String memoryId, TokenCountEstimator estimator, int maxTokens) {
            this.memoryId = memoryId;
            this.estimator = estimator;
            this.maxTokens = maxTokens;
            this.memory = TokenWindowChatMemory.builder()
                    .id(memoryId)
                    .maxTokens(maxTokens, estimator)
                    .build();
        }

        private synchronized ChatMemory memory() {
            return memory;
        }

        private synchronized void recordTurn(String userText, String assistantText, TokenUsage usage) {
            turns.add(new RecordedTurn(
                    turns.size() + 1,
                    userText,
                    estimator.estimateTokenCountInMessage(UserMessage.from(userText)),
                    assistantText,
                    estimator.estimateTokenCountInMessage(AiMessage.from(assistantText)),
                    usage));
        }

        private synchronized ConversationBudget snapshot() {
            List<ChatMessage> activeMessages = memory.messages();
            int usedTokens = estimator.estimateTokenCountInMessages(activeMessages);
            Map<String, Integer> activeFingerprintCounts = activeMessages.stream()
                    .map(ConversationState::fingerprint)
                    .collect(Collectors.toMap(fingerprint -> fingerprint, fingerprint -> 1, Integer::sum, HashMap::new));

            List<BudgetTurn> budgetTurns = turns.stream()
                    .map(turn -> {
                        boolean userActive = consumeFingerprint(activeFingerprintCounts, fingerprint(UserMessage.from(turn.userText())));
                        boolean assistantActive = consumeFingerprint(activeFingerprintCounts,
                                fingerprint(AiMessage.from(turn.assistantText())));
                        return new BudgetTurn(
                                turn.turn(),
                                turn.userText(),
                                turn.userTokens(),
                                userActive,
                                turn.assistantText(),
                                turn.assistantTokens(),
                                assistantActive);
                    })
                    .toList();

            int retainedTurnTokens = budgetTurns.stream()
                    .mapToInt(turn -> (turn.userActiveInWindow() ? turn.userTokens() : 0)
                            + (turn.assistantActiveInWindow() ? turn.assistantTokens() : 0))
                    .sum();

            int evictedMessageTokens = budgetTurns.stream()
                    .mapToInt(turn -> (turn.userActiveInWindow() ? 0 : turn.userTokens())
                            + (turn.assistantActiveInWindow() ? 0 : turn.assistantTokens()))
                    .sum();

            double fillRatio = maxTokens == 0 ? 0D : ((double) usedTokens) / maxTokens;
            String state = fillRatio >= 0.85 ? "danger" : fillRatio >= 0.60 ? "warning" : "ok";
            int availableTokens = Math.max(0, maxTokens - usedTokens);
            int otherRetainedTokens = Math.max(0, usedTokens - retainedTurnTokens);

            TokenUsage lastUsage = turns.isEmpty() ? null : turns.get(turns.size() - 1).requestUsage();
            Integer lastRequestInput = lastUsage != null ? lastUsage.inputTokenCount() : null;
            Integer lastRequestOutput = lastUsage != null ? lastUsage.outputTokenCount() : null;

            return new ConversationBudget(
                    memoryId,
                    usedTokens,
                    maxTokens,
                    fillRatio,
                    state,
                    budgetTurns,
                    retainedTurnTokens,
                    evictedMessageTokens,
                    otherRetainedTokens,
                    availableTokens,
                    lastRequestInput,
                    lastRequestOutput,
                    configuredModelMaxTokens);
        }

        private static String fingerprint(ChatMessage message) {
            if (message instanceof UserMessage userMessage) {
                return "user:" + userMessage.singleText();
            }
            if (message instanceof AiMessage aiMessage) {
                return "assistant:" + aiMessage.text();
            }
            return message.type() + ":" + message.toString();
        }

        private boolean consumeFingerprint(Map<String, Integer> counts, String fingerprint) {
            Integer count = counts.get(fingerprint);
            if (count == null || count == 0) {
                return false;
            }
            if (count == 1) {
                counts.remove(fingerprint);
            } else {
                counts.put(fingerprint, count - 1);
            }
            return true;
        }
    }

    private record RecordedTurn(
            int turn,
            String userText,
            int userTokens,
            String assistantText,
            int assistantTokens,
            TokenUsage requestUsage) {
    }
}
