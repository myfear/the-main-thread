package com.themainthread.fernbank;

import java.time.Instant;
import java.util.List;

public record AdmissionAuditRecord(
        String evaluationId,
        Instant evaluatedAt,
        String principalName,
        List<String> roles,
        String action,
        String requestId,
        boolean transientConnection,
        String skillId,
        String publisher,
        String publisherTrustTier,
        boolean signatureVerified,
        List<String> requestedScopes,
        List<String> declaredCapabilities,
        String runtimeEnvironment,
        boolean allow,
        String outcome,
        String enforcementMode,
        String policyVersion,
        List<PolicyReason> reasons,
        List<PolicyReason> warnings,
        List<String> reasonCodes,
        List<String> warningCodes) {
}
