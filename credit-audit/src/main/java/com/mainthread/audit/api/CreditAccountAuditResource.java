package com.mainthread.audit.api;

import java.util.List;

import com.mainthread.audit.audit.CreditAccountAuditService;
import com.mainthread.audit.audit.CreditAccountAuditService.AuditSnapshot;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

@Path("/accounts/{id}/audit")
@Produces("application/json")
public class CreditAccountAuditResource {

    @Inject
    CreditAccountAuditService auditService;

    @GET
    @Path("/revisions")
    public List<Number> revisions(@PathParam("id") Long accountId) {
        return auditService.revisions(accountId);
    }

    @GET
    @Path("/revisions/{revision}")
    public AuditSnapshot revision(
            @PathParam("id") Long accountId,
            @PathParam("revision") Long revision) {

        AuditSnapshot snapshot = auditService.snapshot(accountId, revision);

        if (snapshot == null) {
            throw new NotFoundException(
                    "No revision " + revision + " for account " + accountId);
        }

        return snapshot;
    }
}