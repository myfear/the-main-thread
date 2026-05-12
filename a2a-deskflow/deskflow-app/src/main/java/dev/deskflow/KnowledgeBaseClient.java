package dev.deskflow;

/**
 * Contract for calling the remote knowledge-base agent. Implementation uses LangChain4j A2A programmatically
 * so we do not mix {@code @RegisterAiService} with agent-only transports (that combination routes through
 * {@code AiServiceMethodImplementationSupport} and expects a {@code @UserMessage} template).
 */
public interface KnowledgeBaseClient {

    String findRemediation(String category, String severity, String summary, String details);
}
