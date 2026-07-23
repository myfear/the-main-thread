package com.themainthread.fernbank;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

@Singleton
public class OpaToolFilter implements ToolFilter {

    private static final Logger LOG = Logger.getLogger(OpaToolFilter.class);

    private final SecurityIdentity identity;
    private final SkillCatalog catalog;
    private final OpaPolicyEvaluator policyEvaluator;
    private final DecisionAudit audit;
    private final FernbankConfig config;

    OpaToolFilter(
            SecurityIdentity identity,
            SkillCatalog catalog,
            OpaPolicyEvaluator policyEvaluator,
            DecisionAudit audit,
            FernbankConfig config) {
        this.identity = identity;
        this.catalog = catalog;
        this.policyEvaluator = policyEvaluator;
        this.audit = audit;
        this.config = config;
    }

    @Override
    @ActivateRequestContext
    public boolean test(ToolInfo tool, FilterContext context) {
        try {
            if (identity.isAnonymous()) {
                LOG.warnf("No authenticated identity available for MCP tool %s; denying access", tool.name());
                return false;
            }

            SkillManifest manifest = catalog.find(tool.name()).orElse(null);
            if (manifest == null) {
                LOG.errorf("No skill manifest found for MCP tool %s; denying access", tool.name());
                return false;
            }

            SubjectContext subject = new SubjectContext(
                    identity.getPrincipal().getName(),
                    identity.getRoles().stream().sorted().toList());
            AdmissionInput input = new AdmissionInput(
                    subject,
                    manifest,
                    config.runtimeEnvironment(),
                    "mcp:tool:access");

            PolicyDecision decision;
            try {
                decision = policyEvaluator.evaluate(input);
            } catch (RuntimeException e) {
                LOG.errorf(e, "OPA evaluation failed for tool %s; denying access", tool.name());
                decision = PolicyDecision.evaluationFailure(e.getMessage());
            }

            audit.record(
                    input,
                    decision,
                    String.valueOf(context.requestId()),
                    context.connection().isTransient());
            return decision.allow();
        } catch (RuntimeException e) {
            LOG.errorf(e, "MCP authorization failed for tool %s; denying access", tool.name());
            return false;
        }
    }
}
