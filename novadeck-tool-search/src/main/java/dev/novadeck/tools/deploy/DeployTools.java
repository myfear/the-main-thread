package dev.novadeck.tools.deploy;

import dev.novadeck.tools.NovaDeckIds;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Deployment pipeline tools for the NovaDeck ops demo catalog.
 */
@ApplicationScoped
public class DeployTools {

    @Tool("List deployments for an environment (prod|staging|dev).")
    public String listDeployments(String environment) {
        return "deployments[" + environment + "]: " + NovaDeckIds.deployId("r42") + "(rolling),"
                + NovaDeckIds.deployId("r41") + "(complete)";
    }

    @Tool("Fetch deployment status by deployment id.")
    public String getDeploymentStatus(String deployId) {
        return "deployment{" + deployId + ",phase=awaiting_health,progress=62}";
    }

    @Tool("Promote an artifact tag through environments with approval hint.")
    public String promoteArtifact(String artifactTag, String targetEnvironment) {
        return "promote{tag=" + artifactTag + ",to=" + targetEnvironment + ",ticket=CHG-90210}";
    }

    @Tool("Rollback deployment to previous revision with reason.")
    public String rollbackDeploy(String deployId, String reason) {
        return "rollback_initiated{" + deployId + ",reason=" + reason + "}";
    }

    @Tool("Fetch canary metrics snapshot for a deployment id.")
    public String canaryMetrics(String deployId) {
        return "canary{" + deployId + ",error_rate=0.41%,p99_ms=820}";
    }

    @Tool("Pause an in-flight deployment.")
    public String pauseDeploy(String deployId) {
        return "paused{" + deployId + "}";
    }

    @Tool("Resume a paused deployment.")
    public String resumeDeploy(String deployId) {
        return "resumed{" + deployId + "}";
    }

    @Tool("Compare git revision between two deployment ids.")
    public String diffDeployRevisions(String deployIdA, String deployIdB) {
        return "diff{" + deployIdA + "->" + deployIdB + ",commits=7,highlights=schema-migration}";
    }

    @Tool("Schedule deployment window start time in ISO-8601.")
    public String scheduleDeployWindow(String deployId, String isoStartTime) {
        return "scheduled{" + deployId + ",start=" + isoStartTime + "}";
    }
}
