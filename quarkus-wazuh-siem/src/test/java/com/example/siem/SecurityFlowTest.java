package com.example.siem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

@QuarkusTest
class SecurityFlowTest {
    @Inject
    ObjectMapper mapper;

    @Inject
    TestAuditRecorder recorder;

    @Test
    void fourHttpCasesProduceSafeCorrelatedRecords() throws Exception {
        String alice = given().auth().preemptive().basic("alice", "alice-dev-only")
                .get("/api/profile?token=secret-query").then().statusCode(200)
                .body("principal", equalTo("alice")).extract().header("X-Audit-Request-ID");
        String failure = given().auth().preemptive().basic("alice", "wrong-secret")
                .header("X-Forwarded-For", "203.0.113.77").header("X-Audit-Request-ID", "forged")
                .get("/api/profile?password=secret-query").then().statusCode(401)
                .extract().header("X-Audit-Request-ID");
        String denial = given().auth().preemptive().basic("alice", "alice-dev-only")
                .post("/api/admin/export").then().statusCode(403).extract().header("X-Audit-Request-ID");
        String export = given().auth().preemptive().basic("operator", "operator-dev-only")
                .post("/api/admin/export").then().statusCode(200)
                .body("status", equalTo("completed")).extract().header("X-Audit-Request-ID");

        assertEvent(alice, "AUTHENTICATION_SUCCESS", "alice", "/api/profile");
        JsonNode failed = assertEvent(failure, "AUTHENTICATION_FAILURE", null, "/api/profile");
        assertFalse(failed.path("source_ip").isNull());
        assertFalse(failed.path("source_ip").asText().equals("203.0.113.77"));
        assertEvent(denial, "AUTHORIZATION_FAILURE", "alice", "/api/admin/export");
        assertEvent(export, "SENSITIVE_OPERATION_COMPLETED", "operator", "/api/admin/export");
        assertTrue(events(denial).stream().noneMatch(e -> e.path("audit_event_type").asText()
                .equals("SENSITIVE_OPERATION_COMPLETED")));
        for (String id : List.of(alice, failure, denial, export)) {
            for (JsonNode event : events(id)) {
                String json = event.toString();
                for (String secret : List.of("wrong-secret", "dev-only", "secret-query", "Authorization", "forged")) {
                    assertFalse(json.contains(secret), json);
                }
                assertEquals(1, event.path("schema_version").asInt());
                UUID.fromString(event.path("event_id").asText());
                Instant.parse(event.path("timestamp").asText());
            }
        }
    }

    @Test
    void missingCredentialsAreRejected() {
        given().get("/api/profile").then().statusCode(401);
        given().post("/api/admin/export").then().statusCode(401);
    }

    private JsonNode assertEvent(String requestId, String type, String principal, String path) throws Exception {
        assertNotNull(requestId, "The server must generate a correlation ID even on denial");
        UUID.fromString(requestId);
        JsonNode event = events(requestId).stream()
                .filter(e -> e.path("audit_event_type").asText().equals(type)).findFirst().orElseThrow();
        assertEquals(principal, event.path("principal").isNull() ? null : event.path("principal").asText());
        assertEquals(path, event.path("path").asText());
        return event;
    }

    private List<JsonNode> events(String requestId) throws Exception {
        List<JsonNode> records = new ArrayList<>();
        for (String line : recorder.records()) {
            JsonNode record = mapper.readTree(line);
            if (record.path("request_id").asText().equals(requestId)) {
                records.add(record);
            }
        }
        return records;
    }
}
