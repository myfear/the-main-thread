package com.themainthread.carrierwebhooks.transform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.themainthread.carrierwebhooks.api.WebhookProblem;
import com.themainthread.carrierwebhooks.model.NormalizedShipment;
import com.themainthread.carrierwebhooks.model.ShipmentStatus;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@Unremovable
@ApplicationScoped
public class ApprovedTransformerRegistry {

    private final ScriptInterfaceFactory<CarrierWebhookTransformer, Void> factory;
    private final CanonicalEventParser parser;
    private Map<String, TransformerDefinition> definitions;

    public ApprovedTransformerRegistry(
            ScriptInterfaceFactory<CarrierWebhookTransformer, Void> factory,
            CanonicalEventParser parser) {
        this.factory = factory;
        this.parser = parser;
    }

    @PostConstruct
    void loadApprovedTransformers() {
        String source = readResource("transformers/parcelbird-2026-08-29.1.js");
        TransformerDefinition parcelBird = new TransformerDefinition(
                "parcelbird",
                "parcelbird-2026-08-29.1",
                source,
                sha256(source));

        verifyFixture(parcelBird, """
                {"event_id":"pb-1001","parcel":{"tracking":"PB123456"},"event":"parcel.delivered","occurred_at":"2026-08-29T08:15:00Z"}
                """, new NormalizedShipment("parcelbird", "pb-1001", "PB123456", ShipmentStatus.DELIVERED,
                        java.time.Instant.parse("2026-08-29T08:15:00Z")));

        definitions = Map.of(parcelBird.carrier(), parcelBird);
    }

    public TransformationResult transform(String carrier, String payload) {
        TransformerDefinition definition = definitions.get(carrier);
        if (definition == null) {
            throw new WebhookProblem(404, "unknown_carrier", "No approved transformer exists for carrier " + carrier);
        }

        String transformerOutput;
        try {
            CarrierWebhookTransformer transformer = factory.create(definition.source(), null);
            transformerOutput = transformer.normalize(payload);
        } catch (RuntimeException exception) {
            throw new WebhookProblem(422, "transformer_failed", "The approved transformer rejected this webhook");
        }

        return new TransformationResult(definition, parser.parse(carrier, transformerOutput));
    }

    private void verifyFixture(TransformerDefinition definition, String input, NormalizedShipment expected) {
        CarrierWebhookTransformer transformer = factory.create(definition.source(), null);
        NormalizedShipment actual = parser.parse(definition.carrier(), transformer.normalize(input));
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Transformer fixture failed for " + definition.version());
        }
    }

    private String readResource(String location) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalStateException("Missing transformer resource " + location);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read transformer resource " + location, exception);
        }
    }

    private String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM does not provide SHA-256", exception);
        }
    }
}
