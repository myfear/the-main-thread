package com.themainthread.ledgerlock;

import java.math.BigDecimal;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/payouts")
@Produces(MediaType.APPLICATION_JSON)
public class PayoutResource {

    private final JsonWebToken accessToken;

    public PayoutResource(JsonWebToken accessToken) {
        this.accessToken = accessToken;
    }

    @POST
    @RolesAllowed("payout:approve")
    public Response approve(@Valid PayoutRequest request) {
        ApprovedPayout payout = new ApprovedPayout(
                UUID.randomUUID(),
                request.recipient(),
                request.amount(),
                accessToken.getSubject(),
                "APPROVED");
        return Response.status(Response.Status.ACCEPTED).entity(payout).build();
    }

    public record PayoutRequest(
            @NotBlank String recipient,
            @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record ApprovedPayout(
            UUID id,
            String recipient,
            BigDecimal amount,
            String approvedBy,
            String status) {
    }
}
