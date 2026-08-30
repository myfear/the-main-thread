package com.themainthread.carrierwebhooks.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebhookProblemMapper implements ExceptionMapper<WebhookProblem> {

    @Override
    public Response toResponse(WebhookProblem problem) {
        return Response.status(problem.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ProblemResponse(problem.code(), problem.getMessage()))
                .build();
    }
}
