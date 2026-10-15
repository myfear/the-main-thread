package com.example.siem;

import java.util.UUID;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
@Path("/api/admin/export")
public class AdminExportResource {
    private final SecurityIdentity identity;
    private final RoutingContext context;
    private final AuditNormalizer normalizer;
    private final AuditRecorder recorder;

    public AdminExportResource(SecurityIdentity identity, RoutingContext context,
            AuditNormalizer normalizer, AuditRecorder recorder) {
        this.identity = identity;
        this.context = context;
        this.normalizer = normalizer;
        this.recorder = recorder;
    }

    @POST
    @RolesAllowed("admin")
    public ExportResult export() {
        // Creating this result is the entire simulated operation; no real records are exported.
        var result = new ExportResult(UUID.randomUUID().toString(), "completed", 0);
        recorder.record(normalizer.normalize(SecurityAuditRecord.EventType.SENSITIVE_OPERATION_COMPLETED,
                identity, context));
        return result;
    }

    public record ExportResult(String exportId, String status, int records) {
    }
}
