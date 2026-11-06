// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<AirportFailure> {
    public record ApiError(String code, String message) {
    }

    @Override
    public Response toResponse(AirportFailure failure) {
        return Response.status(failure.kind().status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiError(failure.kind().name(), failure.kind().message()))
                .build();
    }
}
