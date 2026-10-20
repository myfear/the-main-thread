package com.themainthread.time;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException webException) {
            return Response.status(webException.getResponse().getStatus())
                    .entity(new ApiError(webException.getMessage())).type("application/json").build();
        }
        LOG.error("Time experiment failed", exception);
        return Response.serverError()
                .entity(new ApiError(
                        "The experiment could not finish. Check the application log and PostgreSQL connection, then retry."))
                .type("application/json").build();
    }

    public record ApiError(String message) {
    }
}
