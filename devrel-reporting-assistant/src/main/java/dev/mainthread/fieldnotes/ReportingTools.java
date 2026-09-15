package dev.mainthread.fieldnotes;

import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import io.vertx.ext.web.RoutingContext;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ReportingTools {
    private final ReportService reports;
    private final RoutingContext request;
    public ReportingTools(ReportService reports, RoutingContext request) { this.reports = reports; this.request = request; }

    private ReportSession session() {
        return reports.forToken(request.request().getHeader("X-Report-Token"));
    }

    @Tool(description = "Read the draft, current revision, authoritative field catalog, and applicable validation issues. Read this before updating or asking questions.")
    public Map<String, Object> getReport() { return session().view(); }

    @Tool(description = "Record facts from the user's notes. Use only field IDs and typed values from getReport.catalog. Null clears a field. Set controlling fields before dependent ones. Returns new revision, cleared fields, and validation issues. Never invent missing facts.")
    public Map<String, Object> updateReport(long expectedRevision,
            @ToolArg(description = "Object mapping catalog field IDs to strings, numbers, booleans, or null. No derived/status fields.") Map<String, Object> values) {
        return session().update(expectedRevision, values);
    }

    @Tool(description = "Display 1–4 related applicable report fields in the browser and WAIT for the user. Choose catalog IDs, not arbitrary schemas. The returned report already includes submitted answers. Correct validation issues before unrelated questions. On cancellation or expiry, stop and finish the turn.")
    public Uni<Map<String, Object>> showForm(long expectedRevision, List<String> fieldIds, String question) {
        return Uni.createFrom().completionStage(session().showForm(expectedRevision, fieldIds, question));
    }
}
