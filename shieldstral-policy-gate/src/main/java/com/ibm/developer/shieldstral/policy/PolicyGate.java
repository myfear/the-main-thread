package com.ibm.developer.shieldstral.policy;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.ibm.developer.shieldstral.config.SafetyPoliciesConfig;

@ApplicationScoped
public final class PolicyGate {

    private static final Logger LOG = Logger.getLogger(PolicyGate.class);

    private final PolicyClassifier classifier;
    private final SafetyPoliciesConfig policies;

    PolicyGate(PolicyClassifier classifier, SafetyPoliciesConfig policies) {
        this.classifier = classifier;
        this.policies = policies;
    }

    public SafetyAssessment evaluate(PolicySurface surface, PolicyDirection direction, String document) {
        SafetyPoliciesConfig.Policy policy = policy(surface);
        String query = direction == PolicyDirection.INPUT ? policy.inputQuery() : policy.outputQuery();

        try {
            ClassifierScore classification = classifier.classify(
                    new ClassifierRequest(policy.instruction(), query, document));
            boolean blocked = classification.unsafeScore() > policy.threshold();
            return new SafetyAssessment(
                    surface.path(),
                    direction,
                    blocked ? SafetyStatus.BLOCK : SafetyStatus.ALLOW,
                    classification.unsafeScore(),
                    policy.threshold(),
                    blocked,
                    blocked ? "unsafe score exceeded the policy threshold" : "unsafe score stayed within the policy threshold");
        } catch (RuntimeException failure) {
            LOG.warnf("Shieldstral classification failed for policy %s and direction %s: %s",
                    surface.path(), direction.path(), failure.getMessage());
            return new SafetyAssessment(
                    surface.path(),
                    direction,
                    SafetyStatus.INDETERMINATE,
                    null,
                    policy.threshold(),
                    policy.failClosed(),
                    policy.failClosed() ? "classifier unavailable; fail-closed policy applied"
                            : "classifier unavailable; fail-open policy applied");
        }
    }

    private SafetyPoliciesConfig.Policy policy(PolicySurface surface) {
        return switch (surface) {
            case PUBLIC_SUPPORT -> policies.publicSupport();
            case SECURITY_RESEARCH -> policies.securityResearch();
        };
    }
}
