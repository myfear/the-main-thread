package com.pixel.api;

import java.util.Map;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HttpErrors implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException error) {
        int status = error.getResponse().getStatus();
        String message = switch (status) {
            case 400 -> "Invalid image or blockSize; use PNG/JPEG and blockSize within the configured range and image dimensions";
            case 413 -> "Image exceeds the configured size limit";
            case 503 -> "All conversion slots are occupied; retry later";
            default -> "Request could not be completed";
        };
        return Response.fromResponse(error.getResponse()).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", message)).build();
    }
}
