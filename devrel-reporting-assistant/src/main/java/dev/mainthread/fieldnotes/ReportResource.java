package dev.mainthread.fieldnotes;

import java.util.List;
import java.util.Map;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import io.smallrye.mutiny.Multi;

@Path("/api/report")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {
    public record Create(String month, String author) {}
    public record Answer(long expectedRevision, Map<String, Object> values) {}
    public record Save(long expectedRevision) {}
    public record Message(String id, String role, String content) {}
    public record RunInput(String threadId, String runId, List<Message> messages) {}
    private final ReportService reports;
    public ReportResource(ReportService reports) { this.reports = reports; }

    @POST
    public Map<String, Object> create(Create input) {
        if (input == null || input.month() == null) throw new IllegalArgumentException("Choose a reporting month");
        return reports.create(input.month(), input.author() == null ? "Taylor Quinn" : input.author()).view();
    }
    @GET
    public Map<String, Object> get() { return reports.current().view(); }
    @DELETE
    public Map<String, Object> reset() { reports.reset(); return Map.of("status", "reset"); }
    @POST @Path("/stop")
    public Map<String, Object> stop() { return reports.stop(); }
    @POST @Path("/forms/{id}/answers")
    public Map<String, Object> answer(@PathParam("id") String id, Answer input) {
        if (input == null) throw new IllegalArgumentException("Supply answers");
        return reports.current().answer(id, input.expectedRevision(), input.values());
    }
    @POST @Path("/save")
    public Map<String, Object> save(Save input) {
        if (input == null) throw new IllegalArgumentException("Supply the current revision");
        return reports.current().save(input.expectedRevision());
    }
    @GET @Path("/download")
    public Map<String, Object> download() {
        Map<String, Object> view = get();
        if (!"saved".equals(view.get("status"))) throw new Problem(409, "Save the report before downloading");
        return Map.of("id", view.get("id"), "month", view.get("month"), "values", view.get("values"));
    }
    @POST @Path("/agent") @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> run(RunInput input, @Context Sse sse) {
        if (input == null || input.messages() == null || input.messages().isEmpty()) throw new IllegalArgumentException("Supply a user message");
        Message last = input.messages().get(input.messages().size() - 1);
        if (last == null || !"user".equals(last.role())) throw new IllegalArgumentException("The latest message must be from the user");
        ReportSession session = reports.current();
        long cursor = reports.run(input.threadId(), input.runId(), last.content());
        return events(session, cursor, input.runId(), sse);
    }
    @GET @Path("/events") @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> reconnect(@QueryParam("after") @DefaultValue("0") long after,
            @QueryParam("runId") String runId, @Context Sse sse) {
        if (runId == null) throw new IllegalArgumentException("Supply a run ID");
        return events(reports.current(), after, runId, sse);
    }
    private Multi<OutboundSseEvent> events(ReportSession session, long after, String runId, Sse sse) {
        return session.stream(after, runId).map(event -> sse.newEventBuilder().id(Long.toString(event.sequence()))
                .mediaType(MediaType.APPLICATION_JSON_TYPE).data(Map.class, event.data()).build());
    }
}
