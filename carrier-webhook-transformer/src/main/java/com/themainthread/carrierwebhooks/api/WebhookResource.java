package com.themainthread.carrierwebhooks.api;

import java.util.Locale;

import com.themainthread.carrierwebhooks.ledger.ProcessingResult;
import com.themainthread.carrierwebhooks.ledger.WebhookLedger;
import com.themainthread.carrierwebhooks.security.WebhookSignatureVerifier;
import com.themainthread.carrierwebhooks.transform.ApprovedTransformerRegistry;
import com.themainthread.carrierwebhooks.transform.TransformationResult;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/webhooks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WebhookResource {

    private final WebhookSignatureVerifier signatureVerifier;
    private final ApprovedTransformerRegistry registry;
    private final WebhookLedger ledger;

    public WebhookResource(
            WebhookSignatureVerifier signatureVerifier,
            ApprovedTransformerRegistry registry,
            WebhookLedger ledger) {
        this.signatureVerifier = signatureVerifier;
        this.registry = registry;
        this.ledger = ledger;
    }

    @POST
    @Path("/{carrier}")
    public Response receive(
            @PathParam("carrier") String carrier,
            @HeaderParam("X-Carrier-Signature") String signature,
            String payload) {
        if (payload == null || payload.isBlank()) {
            throw new WebhookProblem(400, "empty_payload", "A JSON webhook payload is required");
        }

        signatureVerifier.verify(signature, payload);
        TransformationResult transformation = registry.transform(carrier.toLowerCase(Locale.ROOT), payload);
        ProcessingResult processing = ledger.record(transformation.shipment());

        WebhookReceipt receipt = new WebhookReceipt(
                processing.duplicate() ? "duplicate" : "accepted",
                transformation.definition().version(),
                transformation.definition().sha256(),
                transformation.shipment());

        return Response.status(processing.duplicate() ? Response.Status.OK : Response.Status.ACCEPTED)
                .entity(receipt)
                .build();
    }
}
