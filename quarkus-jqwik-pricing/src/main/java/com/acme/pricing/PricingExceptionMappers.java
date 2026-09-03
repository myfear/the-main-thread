package com.acme.pricing;

import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

class PricingExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(IllegalArgumentException exception) {
        ErrorResponse error = new ErrorResponse("invalid_price_request", exception.getMessage());
        return RestResponse.status(Response.Status.BAD_REQUEST, error);
    }
}
