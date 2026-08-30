package com.themainthread.carrierwebhooks;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WebhookResourceTest {

    private static final String SECRET = "local-demo-secret-change-before-deploy";
    private static final String ACCEPTED_DELIVERY = """
            {"event_id":"pb-1000","parcel":{"tracking":"PB123456"},"event":"parcel.delivered","occurred_at":"2026-08-29T08:15:00Z"}
            """;
    private static final String DUPLICATE_DELIVERY = """
            {"event_id":"pb-1001","parcel":{"tracking":"PB654321"},"event":"parcel.delivered","occurred_at":"2026-08-29T08:15:00Z"}
            """;

    @Test
    void acceptsASignedWebhookAndReturnsItsTransformerIdentity() {
        given()
                .header("X-Carrier-Signature", signature(ACCEPTED_DELIVERY))
                .contentType("application/json")
                .body(ACCEPTED_DELIVERY)
                .when().post("/webhooks/parcelbird")
                .then()
                .statusCode(202)
                .body("result", equalTo("accepted"))
                .body("transformerVersion", equalTo("parcelbird-2026-08-29.1"))
                .body("transformerSha256", notNullValue())
                .body("shipment.eventId", equalTo("pb-1000"))
                .body("shipment.status", equalTo("DELIVERED"));
    }

    @Test
    void acceptsTheSameWebhookOnlyOnce() {
        given()
                .header("X-Carrier-Signature", signature(DUPLICATE_DELIVERY))
                .contentType("application/json")
                .body(DUPLICATE_DELIVERY)
                .when().post("/webhooks/parcelbird")
                .then().statusCode(202);

        given()
                .header("X-Carrier-Signature", signature(DUPLICATE_DELIVERY))
                .contentType("application/json")
                .body(DUPLICATE_DELIVERY)
                .when().post("/webhooks/parcelbird")
                .then()
                .statusCode(200)
                .body("result", equalTo("duplicate"));
    }

    @Test
    void rejectsAnInvalidSignatureBeforeTheTransformerRuns() {
        given()
                .header("X-Carrier-Signature", "sha256=" + "0".repeat(64))
                .contentType("application/json")
                .body(ACCEPTED_DELIVERY)
                .when().post("/webhooks/parcelbird")
                .then()
                .statusCode(401)
                .body("code", equalTo("invalid_signature"));
    }

    @Test
    void rejectsAWebhookTheApprovedTransformerCannotClassify() {
        String unknownEvent = """
                {"event_id":"pb-1002","parcel":{"tracking":"PB654321"},"event":"parcel.teleported","occurred_at":"2026-08-29T08:15:00Z"}
                """;

        given()
                .header("X-Carrier-Signature", signature(unknownEvent))
                .contentType("application/json")
                .body(unknownEvent)
                .when().post("/webhooks/parcelbird")
                .then()
                .statusCode(422)
                .body("code", equalTo("transformer_failed"));
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
