package dev.topology.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.CreatedAware;

@CreatedAware
public interface DispatchSpecialist {

    @Agent("Recommend an ownership handoff and next action")
    String dispatch(@V("request") String request);
}
