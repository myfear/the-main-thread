package com.themainthread.gluhwein;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiErrors implements ExceptionMapper<WebApplicationException> {
    public record Problem(String message) {
    }

    @Override
    public Response toResponse(WebApplicationException exception) {
        return Response.status(exception.getResponse().getStatus())
                .type("application/json").entity(new Problem(exception.getMessage())).build();
    }
}
