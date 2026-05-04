package dev.topology.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import io.quarkiverse.langchain4j.CreatedAware;

@CreatedAware
public interface RiskSpecialist {

    @Agent("Assess operational and customer-impact risk")
    String assessRisk(@V("request") String request);
}
