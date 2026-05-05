package dev.conduit.workflow.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.CreatedAware;

@CreatedAware
public interface SummarizeHandoffSpecialist {

    @Agent
    @UserMessage("Produce under 140 characters summarizing severity and fingerprint for ops. Severity: {{severity_label}}. Fingerprint: {{content_fingerprint}}. Payload: {{payload_snippet}}")
    String summarize(
            @V("severity_label") String severityLabel,
            @V("content_fingerprint") String contentFingerprint,
            @V("payload_snippet") String payloadSnippet);
}