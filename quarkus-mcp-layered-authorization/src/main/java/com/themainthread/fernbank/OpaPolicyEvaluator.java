package com.themainthread.fernbank;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.styra.opa.wasm.OpaPolicy;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OpaPolicyEvaluator {

    private static final String POLICY_PATH = "/policies/skill-admission.wasm";

    private final ObjectMapper objectMapper;
    private OpaPolicy policy;

    OpaPolicyEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadPolicy() {
        InputStream stream = OpaPolicyEvaluator.class.getResourceAsStream(POLICY_PATH);
        if (stream == null) {
            throw new IllegalStateException("OPA policy not found: " + POLICY_PATH);
        }
        policy = OpaPolicy.builder().withPolicy(stream).build();
    }

    public PolicyDecision evaluate(AdmissionInput input) {
        try {
            String resultJson = policy.evaluate(objectMapper.writeValueAsString(input));
            JsonNode result = objectMapper.readTree(resultJson).path(0).path("result");
            if (result.isMissingNode() || result.isNull()) {
                throw new IllegalStateException("OPA returned no decision");
            }
            return objectMapper.treeToValue(result, PolicyDecision.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("OPA policy evaluation failed", e);
        }
    }
}
