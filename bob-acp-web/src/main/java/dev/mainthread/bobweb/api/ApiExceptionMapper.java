package dev.mainthread.bobweb.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import dev.mainthread.bobweb.api.ApiModels.ApiError;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.status()).entity(new ApiError(exception.getMessage())).build();
    }
}
