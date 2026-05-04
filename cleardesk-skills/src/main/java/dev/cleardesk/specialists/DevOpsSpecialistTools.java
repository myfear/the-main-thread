package dev.cleardesk.specialists;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

/**
 * Stub tools for developer operations (CI/CD, deploys, clusters).
 */
@ApplicationScoped
public class DevOpsSpecialistTools {

    private static final Logger LOG = Logger.getLogger(DevOpsSpecialistTools.class);

    @Tool("Triggers a deploy placeholder for a service (platform scope).")
    public String triggerDeployPreview(String serviceName) {
        LOG.debugf("deploy preview %s", serviceName);
        return "devops:deploy:" + serviceName;
    }

    @Tool("Reads mock CI pipeline status.")
    public String checkPipeline(String pipelineId) {
        LOG.debugf("pipeline check: %s", pipelineId);
        return "devops:pipeline:" + pipelineId;
    }
}
