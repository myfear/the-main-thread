package com.themainthread.poolfaults;

import java.sql.SQLException;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.TransactionalException;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class DatabaseFailures {
    private static final Logger LOG = Logger.getLogger(DatabaseFailures.class);

    @ServerExceptionMapper
    public Response persistence(PersistenceException failure) {
        return unavailable(failure);
    }

    @ServerExceptionMapper
    public Response transaction(TransactionalException failure) {
        return unavailable(failure);
    }

    @ServerExceptionMapper
    public Response sql(SQLException failure) {
        return unavailable(failure);
    }

    private static Response unavailable(Exception failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        String sqlState = cause instanceof SQLException sql ? sql.getSQLState() : null;
        LOG.warnf("Database request failed: %s, SQL state %s: %s", cause.getClass().getSimpleName(), sqlState,
                cause.getMessage());
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new Failure("database_unavailable", cause.getClass().getSimpleName(), sqlState)).build();
    }

    public record Failure(String error, String cause, String sqlState) {
    }
}
