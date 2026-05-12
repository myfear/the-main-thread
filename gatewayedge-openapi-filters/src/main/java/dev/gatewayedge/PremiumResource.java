package dev.gatewayedge;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/premium")
@Tag(name = "Premium")
public class PremiumResource {

    @GET
    @Path("/report")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Premium-only operations dashboard fragment")
    public String report() {
        return "premium-report";
    }
}