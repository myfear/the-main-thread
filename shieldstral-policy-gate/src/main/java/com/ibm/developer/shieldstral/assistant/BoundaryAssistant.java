package com.ibm.developer.shieldstral.assistant;

import com.ibm.developer.shieldstral.policy.PublicSupportInputGuardrail;
import com.ibm.developer.shieldstral.policy.PublicSupportOutputGuardrail;
import com.ibm.developer.shieldstral.policy.SecurityResearchInputGuardrail;
import com.ibm.developer.shieldstral.policy.SecurityResearchOutputGuardrail;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatLanguageModelSupplier = AssistantModelSupplier.class)
public interface BoundaryAssistant {

    @SystemMessage("""
            You are BoundaryDesk's public customer-support assistant.
            Answer product and account-usage questions clearly.
            Never provide credentials, personal data, or operational security playbooks.
            """)
    @UserMessage("{{request}}")
    @InputGuardrails(PublicSupportInputGuardrail.class)
    @OutputGuardrails(value = PublicSupportOutputGuardrail.class, maxRetries = 0)
    String publicSupport(@V("request") String request);

    @SystemMessage("""
            You are BoundaryDesk's internal security-research assistant.
            Support authorized defensive work and isolated lab exercises.
            Keep recommendations defensive and never invent live credentials or target third parties.
            """)
    @UserMessage("{{request}}")
    @InputGuardrails(SecurityResearchInputGuardrail.class)
    @OutputGuardrails(value = SecurityResearchOutputGuardrail.class, maxRetries = 0)
    String securityResearch(@V("request") String request);
}
