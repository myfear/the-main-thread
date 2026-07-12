package com.themainthread.fernbank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OpaPolicyEvaluatorTest {

    @Inject
    OpaPolicyEvaluator evaluator;

    @Inject
    SkillCatalog catalog;

    @Test
    void allowsVerifiedInternalSkillInProduction() {
        PolicyDecision decision = evaluate("docs_generate", "content", "prod");

        assertTrue(decision.allow());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    void deniesThirdPartyWriteScopesInProduction() {
        PolicyDecision decision = evaluate("pptx_export", "content", "prod");

        assertFalse(decision.allow());
        assertTrue(decision.reasons().stream().anyMatch(reason -> reason.code().equals("SCOPE_NOT_ALLOWED")));
    }

    @Test
    void deniesUnsignedSkillInProduction() {
        PolicyDecision decision = evaluate("unsigned_status", "platform", "prod");

        assertFalse(decision.allow());
        assertTrue(decision.reasons().stream()
                .anyMatch(reason -> reason.code().equals("PROD_SIGNATURE_REQUIRED")));
    }

    @Test
    void softFlagsThirdPartyWriteScopesInDevelopment() {
        PolicyDecision decision = evaluate("pptx_export", "content", "dev");

        assertTrue(decision.allow());
        assertTrue(decision.warnings().stream().anyMatch(reason -> reason.code().equals("SCOPE_SOFT_FLAG")));
    }

    private PolicyDecision evaluate(String skillId, String team, String environment) {
        SkillManifest manifest = catalog.find(skillId).orElseThrow();
        SubjectContext subject = new SubjectContext("alice", "fern-assistant", "session-42", team);
        return evaluator.evaluate(new AdmissionInput(subject, manifest, environment, "mcp:tool:access"));
    }
}
