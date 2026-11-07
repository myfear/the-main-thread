package com.themainthread.streamhouse.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class InvalidRequestMapper {
    @ServerExceptionMapper
    public Response invalid(IllegalArgumentException exception) {
        return badRequest();
    }

    @ServerExceptionMapper
    public Response invalidJson(JsonProcessingException exception) {
        return badRequest();
    }

    @ServerExceptionMapper
    public Response invalidValue(MismatchedInputException exception) {
        return badRequest();
    }

    @ServerExceptionMapper
    public Response invalidBody(BadRequestException exception) {
        return badRequest();
    }

    @ServerExceptionMapper
    public Response wrappedJsonFailure(WebApplicationException exception) {
        if (exception.getResponse().getStatus() == 400 && exception.getCause() instanceof JsonProcessingException) {
            return badRequest();
        }
        return exception.getResponse();
    }

    private Response badRequest() {
        return Response.status(400).entity(new ApiError("INVALID_REQUEST", "Request does not satisfy the event contract")).build();
    }
}
