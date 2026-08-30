package com.themainthread.carrierwebhooks.transform;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themainthread.carrierwebhooks.api.WebhookProblem;
import com.themainthread.carrierwebhooks.model.NormalizedShipment;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CanonicalEventParser {

    private static final Set<String> REQUIRED_FIELDS = Set.of("carrier", "eventId", "trackingNumber", "status", "occurredAt");

    private final ObjectMapper objectMapper;

    public CanonicalEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedShipment parse(String expectedCarrier, String transformerOutput) {
        JsonNode node;
        try {
            node = objectMapper.readTree(transformerOutput);
        } catch (IOException exception) {
            throw invalidOutput("The transformer did not return JSON");
        }

        if (!node.isObject()) {
            throw invalidOutput("The transformer must return a JSON object");
        }

        Set<String> fields = new HashSet<>();
        Iterator<String> fieldNames = node.fieldNames();
        fieldNames.forEachRemaining(fields::add);
        if (!fields.equals(REQUIRED_FIELDS)) {
            throw invalidOutput("The transformer output must contain only the canonical shipment fields");
        }

        try {
            NormalizedShipment shipment = objectMapper.treeToValue(node, NormalizedShipment.class);
            validate(expectedCarrier, shipment);
            return shipment;
        } catch (IOException | DateTimeParseException exception) {
            throw invalidOutput("The transformer output does not match the canonical shipment schema");
        }
    }

    private void validate(String expectedCarrier, NormalizedShipment shipment) {
        if (!expectedCarrier.equals(shipment.carrier())) {
            throw invalidOutput("The transformer returned a shipment for another carrier");
        }
        requireValue(shipment.eventId(), "eventId");
        requireValue(shipment.trackingNumber(), "trackingNumber");
        if (shipment.status() == null) {
            throw invalidOutput("The transformer output is missing status");
        }
        if (shipment.occurredAt() == null) {
            throw invalidOutput("The transformer output is missing occurredAt");
        }
    }

    private void requireValue(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw invalidOutput("The transformer output has an invalid " + field);
        }
    }

    private WebhookProblem invalidOutput(String message) {
        return new WebhookProblem(422, "invalid_transformer_output", message);
    }
}
