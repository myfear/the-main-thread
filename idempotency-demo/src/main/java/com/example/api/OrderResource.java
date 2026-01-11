package com.example.api;

import com.example.idempotency.Hashing;
import com.example.idempotency.IdempotencyRecord;
import com.example.idempotency.IdempotencyService;
import com.example.idempotency.ProcessedResponse;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Path("/orders")
@Consumes("application/json")
@Produces("application/json")
public class OrderResource {

    @Inject
    IdempotencyService service;

    @Inject
    MeterRegistry registry;

    @POST
    public Response createOrder(
            String payload,
            @HeaderParam("Idempotency-Key") String key) {

        if (key == null || key.isBlank()) {
            throw new BadRequestException("Missing Idempotency-Key");
        }

        String fingerprint = Hashing.sha256(payload);

        IdempotencyRecord record = service.find(key)
                .map(existing -> {
                    registry.counter("idempotency.replays",
                            "endpoint", "orders").increment();
                    validateFingerprint(existing, fingerprint);
                    return existing;
                })
                .orElseGet(() -> service.findOrCreate(key, fingerprint, () -> process(payload)));

        return Response.status(record.statusCode)
                .entity(record.responseBody)
                .build();
    }

    private ProcessedResponse process(String payload) {
        return new ProcessedResponse(201, "{ \"status\": \"created\" }");
    }

    private void validateFingerprint(IdempotencyRecord existing, String current) {
        if (!existing.requestFingerprint.equals(current)) {
            throw new WebApplicationException(
                    Response.status(422)
                            .entity("{\"error\":\"Idempotency key reused with different payload\"}")
                            .type("application/json")
                            .build());
        }
    }
}