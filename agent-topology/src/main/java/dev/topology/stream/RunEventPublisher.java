package dev.topology.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Multi;

/**
 * Bounded replay buffer plus a shared {@link Flow.SubmissionPublisher} so multiple SSE clients see new runs.
 */
@ApplicationScoped
public class RunEventPublisher {

    private final int maxEvents;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedDeque<String> ring = new ConcurrentLinkedDeque<>();
    private final SubmissionPublisher<String> live;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    public RunEventPublisher(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "topology.run-events.max", defaultValue = "50") int maxEvents) {
        this.objectMapper = objectMapper;
        this.maxEvents = maxEvents;
        this.live = new SubmissionPublisher<String>(executor, Flow.defaultBufferSize());
    }

    public void publishSummary(String requestSnippet, String summary) {
        try {
            String line = objectMapper.writeValueAsString(new RunEvent(requestSnippet, summary));
            while (ring.size() >= maxEvents) {
                ring.pollFirst();
            }
            ring.offerLast(line);
            live.submit(line);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Replay recent JSON lines, then stream live submissions (same format as SSE elements).
     */
    public Multi<String> stream() {
        List<String> snapshot = new ArrayList<>(ring);
        Multi<String> past = Multi.createFrom().iterable(snapshot);
        Multi<String> tail = Multi.createFrom().publisher(live);
        return Multi.createBy().concatenating().streams(past, tail);
    }
}
