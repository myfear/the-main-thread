package com.example.siem;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SecurityAuditRecord(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("event_id") String eventId,
        String timestamp,
        String service,
        @JsonProperty("audit_event_type") EventType eventType,
        Outcome outcome,
        String principal,
        @JsonProperty("source_ip") String sourceIp,
        String method,
        String path,
        @JsonProperty("request_id") String requestId) {

    public enum EventType {
        AUTHENTICATION_SUCCESS, AUTHENTICATION_FAILURE, AUTHORIZATION_FAILURE, SENSITIVE_OPERATION_COMPLETED
    }

    public enum Outcome {
        SUCCESS, FAILURE, DENIED
    }
}
