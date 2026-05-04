package dev.novadeck.trace;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory ring buffer of tool-search observations for the demo trace endpoint.
 */
@ApplicationScoped
public class ToolSearchTraceRegistry {

    private static final int MAX = 64;

    private final Deque<ToolSearchTraceEntry> recent = new ArrayDeque<>();

    public synchronized void recordSearchRound(int searchableToolCount, String toolExecutionSummary, List<String> matchedToolNames) {
        recent.addFirst(new ToolSearchTraceEntry(
                Instant.now(),
                searchableToolCount,
                toolExecutionSummary,
                List.copyOf(matchedToolNames)));
        while (recent.size() > MAX) {
            recent.removeLast();
        }
    }

    public synchronized List<ToolSearchTraceEntry> recentEntries() {
        return new ArrayList<>(recent);
    }

    public record ToolSearchTraceEntry(
            Instant timestamp,
            int searchableToolCountAtSearch,
            String toolExecutionSummary,
            List<String> matchedToolNames) {
    }
}
