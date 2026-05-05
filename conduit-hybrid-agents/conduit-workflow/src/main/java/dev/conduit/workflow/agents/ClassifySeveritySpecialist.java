package dev.conduit.workflow.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.CreatedAware;

@CreatedAware
public interface ClassifySeveritySpecialist {

    @Agent
    @UserMessage("Reply with exactly one token: LOW, MEDIUM, or HIGH based on payload severity cues. Canonical record ID: {{canonical_record_id}}. Payload snippet: {{payload_snippet}}")
    String classify(
            @V("canonical_record_id") String canonicalRecordId,
            @V("payload_snippet") String payloadSnippet);
}
