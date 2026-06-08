package com.mainthread.lambdahttp;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/quotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QuoteResource {

    private final ShippingQuoteService shippingQuoteService;

    public QuoteResource(ShippingQuoteService shippingQuoteService) {
        this.shippingQuoteService = shippingQuoteService;
    }

    @GET
    @Path("/{destination}")
    public ShippingQuoteResponse previewFromRequest(
            @PathParam("destination") String destination,
            @QueryParam("speed") @DefaultValue("standard") String speed,
            @QueryParam("weightGrams") @DefaultValue("500") int weightGrams,
            @HeaderParam("X-Customer-Tier") @DefaultValue("standard") String customerTier,
            @Context APIGatewayV2HTTPEvent.RequestContext requestContext) {
        return shippingQuoteService.preview(
                destination,
                weightGrams,
                speed,
                customerTier,
                requestId(requestContext),
                stage(requestContext));
    }

    @POST
    @Path("/preview")
    public ShippingQuoteResponse previewFromBody(
            ShippingQuoteRequest request,
            @Context APIGatewayV2HTTPEvent.RequestContext requestContext) {
        return shippingQuoteService.preview(
                request.destination(),
                request.weightGrams(),
                request.speed(),
                request.customerTier(),
                requestId(requestContext),
                stage(requestContext));
    }

    @ServerExceptionMapper
    RestResponse<ErrorResponse> mapIllegalArgument(IllegalArgumentException exception) {
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, new ErrorResponse(exception.getMessage()));
    }

    private String requestId(APIGatewayV2HTTPEvent.RequestContext requestContext) {
        return requestContext == null ? null : requestContext.getRequestId();
    }

    private String stage(APIGatewayV2HTTPEvent.RequestContext requestContext) {
        return requestContext == null ? null : requestContext.getStage();
    }
}
