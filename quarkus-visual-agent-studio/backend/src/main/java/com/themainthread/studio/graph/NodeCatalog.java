package com.themainthread.studio.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
public class NodeCatalog {
    private final Map<String, Descriptor> descriptors = new LinkedHashMap<>();
    private final WorkflowApplication application;

    public NodeCatalog(WorkflowApplication application, ObjectMapper mapper) throws Exception {
        this.application = application;
        JsonNode empty = mapper.readTree("""
                {"type":"object","additionalProperties":false,"properties":{}}
                """);
        add("workflow.input", "Input", List.of(), List.of(port("fixture", "fixture-id-v1")),
                List.of(), List.of("next"), Map.of(), null, empty);
        add("task.fetch-pr-fixture", "Load fixture", List.of(port("fixture", "fixture-id-v1")),
                List.of(port("pullRequest", "pull-request-v1")), List.of("in"), List.of("next"), Map.of(), null, empty);
        add("ai.agent", "Fixture review agent", List.of(port("pullRequest", "pull-request-v1")),
                List.of(port("reviewResult", "review-result-v1")), List.of("in"), List.of("next"),
                Map.of("model", new Capability("ai.model-ref", true), "prompt", new Capability("ai.prompt-ref", true),
                        "tools", new Capability("ai.toolset-ref", false)), null,
                mapper.readTree("""
                        {"type":"object","additionalProperties":false,"required":["outputSchemaRef","maxToolCalls"],
                         "properties":{"outputSchemaRef":{"const":"review-result-v1"},
                         "maxToolCalls":{"type":"integer","minimum":1,"maximum":5}}}
                        """));
        add("task.review-policy", "Review policy",
                List.of(port("reviewResult", "review-result-v1"), port("pullRequest", "pull-request-v1")),
                List.of(port("requiresApproval", "boolean-v1")), List.of("in"), List.of("next"), Map.of(), null,
                mapper.readTree("""
                        {"type":"object","additionalProperties":false,"required":["minimumSeverity"],
                         "properties":{"minimumSeverity":{"enum":["INFO","LOW","MEDIUM","HIGH","CRITICAL"]}}}
                        """));
        add("logic.switch", "Boolean branch", List.of(port("condition", "boolean-v1")), List.of(),
                List.of("in"), List.of("true", "false"), Map.of(), null, empty);
        add("workflow.output", "Output",
                List.of(port("reviewResult", "review-result-v1"), port("requiresApproval", "boolean-v1")),
                List.of(), List.of("in"), List.of(), Map.of(), null, empty);
        add("ai.model-ref", "Model", List.of(), List.of(), List.of(), List.of(), Map.of(), "model",
                mapper.readTree("""
                        {"type":"object","additionalProperties":false,"required":["modelRef"],
                         "properties":{"modelRef":{"const":"fixture-model-v1"}}}
                        """));
        add("ai.prompt-ref", "Prompt", List.of(), List.of(), List.of(), List.of(), Map.of(), "prompt",
                mapper.readTree("""
                        {"type":"object","additionalProperties":false,"required":["promptRef"],
                         "properties":{"promptRef":{"const":"quarkus-review-v1"}}}
                        """));
        add("ai.toolset-ref", "Read-only tools", List.of(), List.of(), List.of(), List.of(), Map.of(), "tools",
                mapper.readTree("""
                        {"type":"object","additionalProperties":false,"required":["allowedToolIds"],
                         "properties":{"allowedToolIds":{"type":"array","uniqueItems":true,"minItems":1,"maxItems":1,
                         "items":{"const":"pr.read-files"}}}}
                        """));
    }

    private static Port port(String id, String schema) {
        return new Port(id, schema, true);
    }

    private void add(String type, String label, List<Port> inputs, List<Port> outputs, List<String> controlInputs,
            List<String> controlOutputs, Map<String, Capability> capabilities, String capabilityOutput, JsonNode schema) {
        descriptors.put(type, new Descriptor(type, 1, label, capabilityOutput == null, inputs, outputs,
                controlInputs, controlOutputs, capabilities, capabilityOutput, schema));
    }

    public Descriptor get(String type) {
        return descriptors.get(type);
    }

    public List<Descriptor> all() {
        return new ArrayList<>(descriptors.values());
    }

    public boolean validConfig(Descriptor descriptor, JsonNode config) {
        return config != null && application.validatorFactory()
                .getValidator(new SchemaInline(descriptor.configSchema()))
                .validate(application.modelFactory().fromAny(config)).isEmpty();
    }

    public record Port(String id, String schemaRef, boolean required) {
    }

    public record Capability(String type, boolean required) {
    }

    public record Descriptor(String type, int version, String label, boolean executable, List<Port> inputPorts,
            List<Port> outputPorts, List<String> controlInputs, List<String> controlOutputs,
            Map<String, Capability> capabilityInputs, String capabilityOutput, JsonNode configSchema) {
    }
}
