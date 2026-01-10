package com.example.api;

import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GuardrailExceptionMapper implements ExceptionMapper<InputGuardrailException> {

    @Override
    public Response toResponse(InputGuardrailException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("BLOCKED", exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String status, String reason) {
    }
}