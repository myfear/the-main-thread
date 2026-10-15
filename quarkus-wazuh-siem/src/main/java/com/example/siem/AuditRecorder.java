package com.example.siem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class AuditRecorder {
    private static final Logger AUDIT = Logger.getLogger("com.example.siem.audit");
    private static final Logger LOG = Logger.getLogger(AuditRecorder.class);
    private final ObjectMapper mapper;

    public AuditRecorder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String serialize(SecurityAuditRecord record) throws JsonProcessingException {
        return mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(record);
    }

    public void record(SecurityAuditRecord record) {
        try {
            AUDIT.info(serialize(record));
        } catch (JsonProcessingException | RuntimeException failure) {
            // Do not include exception messages, event properties, or request credentials.
            LOG.warn("Security audit emission failed");
        }
    }
}
