package com.themainthread.ledger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/jdbc-metrics")
@Produces(MediaType.APPLICATION_JSON)
public class JdbcMetricsResource {

    private final JdbcCallMetrics metrics;

    public JdbcMetricsResource(JdbcCallMetrics metrics) {
        this.metrics = metrics;
    }

    @GET
    public JdbcCallMetrics.JdbcMetricsSnapshot metrics() {
        return metrics.snapshot();
    }
}
