package dev.signaldesk.api;

public record AssistResponse(String answer, boolean usedTool, String toolName, Outcome outcome) {}
