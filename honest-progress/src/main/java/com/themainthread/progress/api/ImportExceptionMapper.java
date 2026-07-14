package com.themainthread.progress.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ImportExceptionMapper implements ExceptionMapper<ImportRejectedException> {

    @Override
    public Response toResponse(ImportRejectedException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }
}
