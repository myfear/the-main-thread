package dev.novadeck.api;

import java.util.ArrayList;
import java.util.List;

import dev.novadeck.NovaDeckToolCounts;
import dev.novadeck.assistant.FixedOpsAssistant;
import dev.novadeck.assistant.SearchAssistantClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Dev-oriented harness comparing latency between assistants on the same prompt.
 */
@Path("/api/bench")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkResource {

    @Inject
    FixedOpsAssistant fixedOpsAssistant;

    @Inject
    SearchAssistantClient searchAssistantClient;

    @POST
    public BenchResponse bench(ChatRequestBody body) {
        if (body == null || body.prompt == null || body.prompt.isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }
        int runs = body.runs == null || body.runs < 1 ? 1 : Math.min(body.runs, 5);
        String prompt = body.prompt.strip();
        List<Long> fixedMs = new ArrayList<>();
        List<Long> searchMs = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            fixedOpsAssistant.ask(prompt);
            fixedMs.add((System.nanoTime() - t0) / 1_000_000L);
            long t1 = System.nanoTime();
            searchAssistantClient.ask(prompt);
            searchMs.add((System.nanoTime() - t1) / 1_000_000L);
        }
        return new BenchResponse(
                NovaDeckToolCounts.TOTAL_TOOLS,
                prompt,
                runs,
                fixedMs,
                searchMs);
    }

    public record BenchResponse(
            int catalogToolCount,
            String prompt,
            int runs,
            List<Long> fixedMillis,
            List<Long> searchMillis) {
    }
}
