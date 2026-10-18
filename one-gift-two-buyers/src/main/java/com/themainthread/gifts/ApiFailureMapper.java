package com.themainthread.gifts;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiFailureMapper implements ExceptionMapper<ApiFailure> {
    @Override
    public Response toResponse(ApiFailure failure) {
        return Response.status(failure.status).type(MediaType.APPLICATION_JSON).entity(failure.body).build();
    }
}
