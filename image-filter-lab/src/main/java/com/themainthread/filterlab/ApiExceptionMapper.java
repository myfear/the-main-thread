package com.themainthread.filterlab;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ApiExceptionMapper {

    @ServerExceptionMapper
    public Response badRequest(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage() == null ? "Bad request" : exception.getMessage()))
                .build();
    }
}
