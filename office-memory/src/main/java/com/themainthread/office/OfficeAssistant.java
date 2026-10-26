package com.themainthread.office;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService(maxToolCallingRoundTrips = 8, maxToolCallsPerResponse = 6)
@ApplicationScoped
public interface OfficeAssistant {
    @SystemMessage(fromResource = "/office-assistant.txt")
    @ToolBox(OfficeMemoryTools.class)
    String chat(@MemoryId UUID conversationId, @UserMessage String message);
}
