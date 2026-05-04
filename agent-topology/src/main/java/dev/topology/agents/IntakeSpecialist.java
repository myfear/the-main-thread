package dev.topology.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.CreatedAware;

/**
 * First hop: frames the customer signal for downstream specialists.
 *
 * <p>{@link CreatedAware} tells Quarkus LangChain4j build-time processing that this agentic interface is
 * instantiated via {@code AgenticServices.agentBuilder(...)} rather than as a plain {@code AiService}.
 */
@CreatedAware
public interface IntakeSpecialist {

    @Agent("Capture intake notes and urgency for the incident")
    String intake(@V("request") String request);
}
