package dev.deskflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Remote KB lookup over A2A using LangChain4j's programmatic client. Input key order must match the
 * {@code TextPart} order expected by {@code KnowledgeBaseExecutorProducer} on {@code kb-agent}.
 */
@ApplicationScoped
public class A2aKnowledgeBaseClient implements KnowledgeBaseClient {

    private final UntypedAgent remoteKb;

    @Inject
    public A2aKnowledgeBaseClient(@ConfigProperty(name = "deskflow.kb-agent.url") String kbAgentBaseUrl) {
        String base = kbAgentBaseUrl.trim();
        this.remoteKb = AgenticServices.a2aBuilder(base)
                .inputKeys("category", "severity", "summary", "details")
                .outputKey("remediationHint")
                .build();
    }

    @Override
    public String findRemediation(String category, String severity, String summary, String details) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("category", category);
        inputs.put("severity", severity);
        inputs.put("summary", summary);
        inputs.put("details", details);
        Object result = remoteKb.invoke(inputs);
        if (result == null) {
            return "";
        }
        if (result instanceof String s) {
            return s;
        }
        return result.toString();
    }
}
