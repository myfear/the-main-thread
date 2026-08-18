package com.ibm.developer.shieldstral.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import dev.langchain4j.guardrail.GuardrailException;

@Provider
public final class GuardrailExceptionMapper implements ExceptionMapper<GuardrailException> {

    @Override
    public Response toResponse(GuardrailException exception) {
        return Response.status(422)
                .entity(new PolicyProblem(
                        "POLICY_REJECTED",
                        "The request or generated response was rejected by the configured safety policy."))
                .build();
    }
}
