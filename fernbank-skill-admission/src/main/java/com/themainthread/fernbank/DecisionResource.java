package com.themainthread.fernbank;

import java.util.List;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/decisions")
@Produces(MediaType.APPLICATION_JSON)
public class DecisionResource {

    private final DecisionAudit audit;

    DecisionResource(DecisionAudit audit) {
        this.audit = audit;
    }

    @GET
    public List<AdmissionAuditRecord> recent(@QueryParam("limit") @DefaultValue("20") int limit) {
        return audit.recent(limit);
    }
}
