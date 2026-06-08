package dev.themainthread.shipments.api;

import dev.themainthread.shipments.service.TransitionNotAllowedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TransitionNotAllowedExceptionMapper implements ExceptionMapper<TransitionNotAllowedException> {

    @Override
    public Response toResponse(TransitionNotAllowedException exception) {
        return Response.status(Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("transition_not_allowed", exception.getMessage()))
                .build();
    }
}
