package com.themainthread.fernbank;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.vertx.core.http.HttpServerRequest;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

@Singleton
public class OpaToolFilter implements ToolFilter {

    private static final Logger LOG = Logger.getLogger(OpaToolFilter.class);

    private final HttpServerRequest request;
    private final SkillCatalog catalog;
    private final OpaPolicyEvaluator policyEvaluator;
    private final DecisionAudit audit;
    private final FernbankConfig config;

    OpaToolFilter(
            HttpServerRequest request,
            SkillCatalog catalog,
            OpaPolicyEvaluator policyEvaluator,
            DecisionAudit audit,
            FernbankConfig config) {
        this.request = request;
        this.catalog = catalog;
        this.policyEvaluator = policyEvaluator;
        this.audit = audit;
        this.config = config;
    }

    @Override
    public boolean test(ToolInfo tool, FilterContext context) {
        SkillManifest manifest = catalog.find(tool.name()).orElse(null);
        if (manifest == null) {
            LOG.errorf("No skill manifest found for MCP tool %s; denying access", tool.name());
            return false;
        }

        SubjectContext subject = new SubjectContext(
                header("X-Fernbank-User", "anonymous"),
                header("X-Fernbank-Agent", context.connection().initialRequest().implementation().name()),
                header("X-Fernbank-Session", "unknown"),
                header("X-Fernbank-Team", "none"));
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
        audit.record(input, decision);
        return decision.allow();
    }

    private String header(String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
