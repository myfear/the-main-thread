package com.themainthread.poolfaults;

import java.sql.SQLException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.IfBuildProfile;

@Path("/lab/pool")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile(anyOf = { "dev", "test" })
public class PoolLabResource {
    private final AgroalDataSource datasource;

    public PoolLabResource(AgroalDataSource datasource) {
        this.datasource = datasource;
    }

    @GET
    public PoolState state() {
        var metrics = datasource.getMetrics();
        return new PoolState(metrics.activeCount(), metrics.availableCount(), metrics.awaitingCount(),
                metrics.creationCount(), metrics.destroyCount());
    }

    @POST
    @Path("/warm")
    public PoolState warm() throws SQLException {
        // Hold the first connection while borrowing the second, so two physical connections exist.
        try (var first = datasource.getConnection(); var second = datasource.getConnection()) {
            try (var statement = first.createStatement(); var result = statement.executeQuery("select 1")) {
                result.next();
            }
            try (var statement = second.createStatement(); var result = statement.executeQuery("select 1")) {
                result.next();
            }
        }
        return state();
    }

    public record PoolState(long active, long available, long awaiting, long created, long destroyed) {
    }
}
