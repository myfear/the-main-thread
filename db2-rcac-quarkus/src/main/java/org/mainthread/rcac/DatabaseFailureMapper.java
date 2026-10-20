package org.mainthread.rcac;

import java.sql.SQLException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

@Provider
public class DatabaseFailureMapper implements ExceptionMapper<SQLException> {
    private static final Logger LOG = Logger.getLogger(DatabaseFailureMapper.class);

    @Override
    public Response toResponse(SQLException exception) {
        LOG.errorf("Db2 request failed: SQLSTATE=%s, code=%d", exception.getSQLState(), exception.getErrorCode());
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new CustomersResource.ApiError("Database request failed")).build();
    }
}
