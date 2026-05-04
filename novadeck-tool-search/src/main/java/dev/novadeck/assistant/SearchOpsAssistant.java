package dev.novadeck.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Programmatic AI service interface built with {@link dev.langchain4j.service.AiServices}
 * and {@link dev.langchain4j.service.tool.search.ToolSearchStrategy}.
 */
public interface SearchOpsAssistant {

    @SystemMessage("""
            You are NovaDeck with tool discovery enabled. When you need data, search tools first,
            then call the smallest set of tools that answers the question.
            """)
    @UserMessage("{prompt}")
    String ask(String prompt);
}
