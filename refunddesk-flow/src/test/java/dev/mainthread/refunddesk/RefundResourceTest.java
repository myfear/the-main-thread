package dev.mainthread.refunddesk;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class RefundResourceTest {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void autoApprovesSmallRefund() {
        given()
                .contentType(ContentType.JSON)
                .body(new RefundRequest("refund-test-1", "customer-1", new BigDecimal("42.00"), true, 120, 0))
                .when()
                .post("/refunds")
                .then()
                .statusCode(202);

        Response result = waitForResult("refund-test-1");

        result.then()
                .statusCode(200)
                .body("outcome", equalTo("APPROVED"))
                .body("reviewer", equalTo("policy"));
    }

    @Test
    void resumesManualReviewFromCallbackEvent() throws Exception {
        ConsumerTask<Object, Object> flowOut = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .fromTopics("refunddesk-flow-out");

        given()
                .contentType(ContentType.JSON)
                .body(new RefundRequest("refund-test-2", "customer-2", new BigDecimal("450.00"), true, 10, 2))
                .when()
                .post("/refunds")
                .then()
                .statusCode(202);

        CloudEvent reviewRequired = waitForReviewRequiredEvent(flowOut);
        assertEquals("refund.review.required", reviewRequired.getType());
        assertNotNull(reviewRequired.getExtension("flowinstanceid"));

        RefundCase refundCase = objectMapper.readValue(reviewRequired.getData().toBytes(), RefundCase.class);
        assertEquals("refund-test-2", refundCase.request().refundId());

        given()
                .contentType(ContentType.JSON)
                .body(new ReviewDecision("refund-test-2", DecisionOutcome.APPROVED, "test-reviewer", "checked"))
                .when()
                .post("/refunds/{refundId}/review/{instanceId}",
                        "refund-test-2",
                        reviewRequired.getExtension("flowinstanceid").toString())
                .then()
                .statusCode(202);

        Response result = waitForResult("refund-test-2");

        result.then()
                .statusCode(200)
                .body("outcome", equalTo("APPROVED"))
                .body("reviewer", equalTo("test-reviewer"));

        flowOut.close();
    }

    @Test
    void rejectsMismatchedReviewCallback() {
        given()
                .contentType(ContentType.JSON)
                .body(new ReviewDecision("other-refund", DecisionOutcome.APPROVED, "test-reviewer", "checked"))
                .when()
                .post("/refunds/{refundId}/review/{instanceId}",
                        "refund-test-3",
                        "01JFAKEINSTANCE")
                .then()
                .statusCode(400)
                .body("error", equalTo("path refundId and review refundId must match"));
    }

    private Response waitForResult(String refundId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Response response = given()
                    .accept(ContentType.JSON)
                    .when()
                    .get("/refunds/{refundId}", refundId);

            if (response.statusCode() == 200) {
                return response;
            }

            sleep();
        }

        fail("Timed out waiting for refund result " + refundId);
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private CloudEvent waitForReviewRequiredEvent(ConsumerTask<Object, Object> flowOut) {
        AtomicReference<CloudEvent> reviewRequired = new AtomicReference<>();

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            flowOut.stream().forEach(record -> {
                CloudEvent event = CE_JSON.deserialize((byte[]) record.value());
                if ("refund.review.required".equals(event.getType())) {
                    reviewRequired.set(event);
                }
            });
            assertNotNull(reviewRequired.get());
        });

        return reviewRequired.get();
    }
}
