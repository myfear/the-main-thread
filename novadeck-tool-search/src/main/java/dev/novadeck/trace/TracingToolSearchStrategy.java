package dev.novadeck.trace;

import java.util.List;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

import org.jboss.logging.Logger;

/**
 * Delegates to a real {@link ToolSearchStrategy} and records search rounds for the demo UI/logs.
 */
public class TracingToolSearchStrategy implements ToolSearchStrategy {

    private static final Logger LOG = Logger.getLogger(TracingToolSearchStrategy.class);

    private final ToolSearchStrategy delegate;
    private final ToolSearchTraceRegistry registry;

    public TracingToolSearchStrategy(ToolSearchStrategy delegate, ToolSearchTraceRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
        return delegate.getToolSearchTools(invocationContext);
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest toolSearchRequest) {
        int searchable = toolSearchRequest.searchableTools().size();
        String summary = String.valueOf(toolSearchRequest.toolExecutionRequest());
        LOG.infof("NovaDeck tool search: searchableTools=%d execution=%s", searchable, summary);
        ToolSearchResult result = delegate.search(toolSearchRequest);
        List<String> names = result.foundToolNames();
        registry.recordSearchRound(searchable, summary, names);
        LOG.infof("NovaDeck tool search result: matches=%s", names);
        return result;
    }
}
