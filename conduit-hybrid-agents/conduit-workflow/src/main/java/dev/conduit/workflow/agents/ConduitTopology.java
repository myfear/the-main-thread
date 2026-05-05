package dev.conduit.workflow.agents;

import java.util.Map;

import org.jboss.logging.Logger;

import dev.conduit.workflow.mcp.ConduitMcpBridge;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.mcp.McpAgent;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConduitTopology {

    private static final Logger LOG = Logger.getLogger(ConduitTopology.class);

    private final ChatModel chatModel;
    private final ConduitMcpBridge mcpBridge;
    private final AgentMonitor monitor;

    private UntypedAgent pipeline;

    @Inject
    public ConduitTopology(ChatModel chatModel, ConduitMcpBridge mcpBridge) {
        this.chatModel = chatModel;
        this.mcpBridge = mcpBridge;
        this.monitor = new AgentMonitor();
    }

    @PostConstruct
    void buildPipeline() {
        UntypedAgent normalize = McpAgent.builder(mcpBridge.client())
                .toolName("conduit_normalize_record")
                .inputKeys("rawId")
                .outputKey("canonical_record_id")
                .build();

        ClassifySeveritySpecialist classify = AgenticServices.agentBuilder(ClassifySeveritySpecialist.class)
                .chatModel(chatModel)
                .name("classifySeverity")
                .outputKey("severity_label")
                .build();

        UntypedAgent fingerprint = McpAgent.builder(mcpBridge.client())
                .toolName("conduit_fingerprint_payload")
                .inputKeys("payload_snippet")
                .outputKey("content_fingerprint")
                .build();

        SummarizeHandoffSpecialist summarize = AgenticServices.agentBuilder(SummarizeHandoffSpecialist.class)
                .chatModel(chatModel)
                .name("summarizeHandoff")
                .outputKey("handoff_summary")
                .build();

        RouteQueueSpecialist route = AgenticServices.agentBuilder(RouteQueueSpecialist.class)
                .chatModel(chatModel)
                .name("routeQueue")
                .outputKey("target_queue")
                .build();

        pipeline = AgenticServices.sequenceBuilder()
                .subAgents(normalize, classify, fingerprint, summarize, route)
                .listener(monitor)
                .name("conduitPipeline")
                .description("Conduit hybrid demo: deterministic MCP agents plus LLM classification chain")
                .outputKey("target_queue")
                .build();
    }

    public AgentMonitor monitor() {
        return monitor;
    }

    public String run(Map<String, Object> inputs) {
        Object out = pipeline.invoke(inputs);
        String queue = out != null ? out.toString() : "";
        LOG.infov("Conduit finished target_queue={0}", queue);
        return queue;
    }
}