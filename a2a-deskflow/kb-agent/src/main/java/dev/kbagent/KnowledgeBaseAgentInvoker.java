package dev.kbagent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

/**
 * Invokes the request-scoped {@link KnowledgeBaseAgent} from non-request
 * threads (for example the
 * A2A agent executor pool), where CDI's request context is not active by
 * default.
 */
@ApplicationScoped
public class KnowledgeBaseAgentInvoker {

    private final KnowledgeBaseAgent kbAgent;

    @Inject
    public KnowledgeBaseAgentInvoker(KnowledgeBaseAgent kbAgent) {
        this.kbAgent = kbAgent;
    }

    @ActivateRequestContext
    public String findRemediation(String category, String severity, String summary, String details) {
        return kbAgent.findRemediation(category, severity, summary, details);
    }
}
