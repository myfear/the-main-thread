package com.themainthread.office;

import java.util.Map;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiErrors implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception error) {
        int status = 500;
        String message = "The request could not be completed. Refresh the page and try again.";
        if (error instanceof WebApplicationException web) {
            status = web.getResponse().getStatus();
            message = status < 500 || status == 503 ? web.getMessage() : message;
        } else if (error instanceof OptimisticLockException) {
            status = 409;
            message = "This record changed. Refresh the page and try again.";
        }
        return Response.status(status).entity(Map.of("error", message)).build();
    }
}
