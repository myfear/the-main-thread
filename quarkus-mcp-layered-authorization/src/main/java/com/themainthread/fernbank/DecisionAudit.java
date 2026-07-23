package com.themainthread.fernbank;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class DecisionAudit {

    private static final Logger LOG = Logger.getLogger(DecisionAudit.class);
    private static final int CAPACITY = 100;

    private final ObjectMapper objectMapper;
    private final Deque<AdmissionAuditRecord> records = new ArrayDeque<>(CAPACITY);

    DecisionAudit(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized AdmissionAuditRecord record(
            AdmissionInput input,
            PolicyDecision decision,
            String requestId,
            boolean transientConnection) {
        AdmissionAuditRecord record = new AdmissionAuditRecord(
                UUID.randomUUID().toString(),
                Instant.now(),
                input.subject().principalName(),
                List.copyOf(input.subject().roles()),
                input.action(),
                requestId,
                transientConnection,
                input.skill().skillId(),
                input.skill().publisher(),
                input.skill().publisherTrustTier(),
                input.skill().signatureVerified(),
                List.copyOf(input.skill().requestedScopes()),
                List.copyOf(input.skill().declaredCapabilities()),
                input.runtimeEnvironment(),
                decision.allow(),
                decision.outcome(),
                decision.enforcementMode(),
                decision.policyVersion(),
                List.copyOf(decision.reasons()),
                List.copyOf(decision.warnings()),
                codes(decision.reasons()),
                codes(decision.warnings()));

        if (records.size() == CAPACITY) {
            records.removeFirst();
        }
        records.addLast(record);
        log(record);
        return record;
    }

    public synchronized List<AdmissionAuditRecord> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, CAPACITY));
        List<AdmissionAuditRecord> snapshot = new ArrayList<>(records);
        int fromIndex = Math.max(0, snapshot.size() - safeLimit);
        return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
    }

    private List<String> codes(List<PolicyReason> reasons) {
        return reasons.stream().map(PolicyReason::code).sorted().toList();
    }

    private void log(AdmissionAuditRecord record) {
        try {
            LOG.infof("opa_decision=%s", objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Could not serialize OPA decision %s", record.evaluationId());
        }
    }
}
