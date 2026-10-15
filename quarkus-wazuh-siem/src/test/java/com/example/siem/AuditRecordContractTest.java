package com.example.siem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.security.runtime.QuarkusSecurityIdentity;

import org.junit.jupiter.api.Test;

class AuditRecordContractTest {
    private final AuditNormalizer normalizer = new AuditNormalizer();

    @Test
    void noHttpContextOrIdentityStillProducesARecord() {
        var record = normalizer.normalize(SecurityAuditRecord.EventType.AUTHENTICATION_FAILURE, null, null);
        assertNull(record.principal());
        assertNull(record.path());
        assertNull(record.sourceIp());
        assertEquals(SecurityAuditRecord.Outcome.FAILURE, record.outcome());
        assertNotEquals(record.eventId(), record.requestId());
    }

    @Test
    void authenticationFailureNeverClaimsAValidatedPrincipal() {
        var identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "claimed-name").build();
        var record = normalizer.normalize(SecurityAuditRecord.EventType.AUTHENTICATION_FAILURE, identity, null);
        assertNull(record.principal());
        assertEquals(SecurityAuditRecord.Outcome.DENIED,
                normalizer.normalize(SecurityAuditRecord.EventType.AUTHORIZATION_FAILURE, identity, null).outcome());
    }

    @Test
    void jacksonEscapesLineBreaksAndEmitsOnlyTheContract() throws Exception {
        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var recorder = new AuditRecorder(mapper);
        var identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "name\nwith\rbreaks").build();
        var record = normalizer.normalize(SecurityAuditRecord.EventType.AUTHENTICATION_SUCCESS, identity, null);
        String json = recorder.serialize(record);
        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\r"));
        var tree = mapper.readTree(json);
        assertEquals("name\nwith\rbreaks", tree.path("principal").asText());
        Set<String> names = new java.util.HashSet<>();
        tree.fieldNames().forEachRemaining(names::add);
        assertEquals(Set.of("schema_version", "event_id", "timestamp", "service", "audit_event_type", "outcome",
                "principal", "source_ip", "method", "path", "request_id"), names);
    }
}
