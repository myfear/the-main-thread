package dev.topology.agents;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Small sequential specialist chain with a single {@link AgentMonitor} attached to the root workflow.
 * The monitor feeds {@link dev.langchain4j.agentic.observability.HtmlReportGenerator}.
 */
@ApplicationScoped
public class TriageTopology {

    private final ChatModel chatModel;
    private final AgentMonitor monitor;

    private UntypedAgent pipeline;

    @Inject
    public TriageTopology(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.monitor = new AgentMonitor();
    }

    @PostConstruct
    void buildPipeline() {
        IntakeSpecialist intake =
                AgenticServices.agentBuilder(IntakeSpecialist.class).chatModel(chatModel).name("intake").build();
        RiskSpecialist risk =
                AgenticServices.agentBuilder(RiskSpecialist.class).chatModel(chatModel).name("risk").build();
        DispatchSpecialist dispatch =
                AgenticServices.agentBuilder(DispatchSpecialist.class).chatModel(chatModel).name("dispatch").build();

        pipeline = AgenticServices.sequenceBuilder()
                .subAgents(intake, risk, dispatch)
                .listener(monitor)
                .name("triagePipeline")
                .description("Linear triage: intake, risk, dispatch")
                .outputKey("summary")
                .build();
    }

    public AgentMonitor monitor() {
        return monitor;
    }

    /**
     * Runs the whole pipeline once; LangChain4j records invocations on {@link #monitor()}.
     */
    public String run(String userRequest) {
        Object out = pipeline.invoke(Map.of("request", userRequest));
        return out != null ? out.toString() : "";
    }
}
