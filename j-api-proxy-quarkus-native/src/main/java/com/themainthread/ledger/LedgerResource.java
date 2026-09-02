package com.themainthread.ledger;

import java.math.BigDecimal;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/ledger")
@Produces(MediaType.APPLICATION_JSON)
public class LedgerResource {

    private final LedgerRepository repository;

    public LedgerResource(LedgerRepository repository) {
        this.repository = repository;
    }

    @GET
    @Path("/{accountId}")
    public LedgerBalance balance(@PathParam("accountId") String accountId) {
        return new LedgerBalance(accountId, repository.balance(accountId));
    }

    public record LedgerBalance(String accountId, BigDecimal balance) {
    }
}
