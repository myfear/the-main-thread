package com.themainthread.pglite;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.smallrye.common.annotation.Blocking;

@Path("/pglite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class PGliteResource {
    private final PGliteService service;

    public PGliteResource(PGliteService service) {
        this.service = service;
    }

    public record SqlRequest(String sql) {
    }

    public record TransactionRequest(List<String> statements) {
    }

    public record SqlError(String sqlState, String message) {
    }

    @GET
    public Map<String, String> status() {
        var database = service.database();
        return Map.of("instanceId", database.instanceId().toString(), "transactionStatus", database.transactionStatus());
    }

    @POST
    @Path("/sql")
    public QueryResult query(SqlRequest request) {
        if (request == null || request.sql() == null || request.sql().isBlank()) {
            throw new BadRequestException("Provide a nonempty sql field");
        }
        return service.database().execute(request.sql());
    }

    @POST
    @Path("/transaction")
    public List<QueryResult> transaction(TransactionRequest request) {
        if (request == null || request.statements() == null || request.statements().isEmpty()) {
            throw new BadRequestException("Provide a nonempty statements array");
        }
        for (String sql : request.statements()) {
            if (sql == null || sql.isBlank()) {
                throw new BadRequestException("Transaction statements must be nonempty");
            }
        }
        return service.transaction(request.statements());
    }

    @ServerExceptionMapper
    Response sqlFailure(SqlException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new SqlError(exception.sqlState(), exception.getMessage())).build();
    }
}
