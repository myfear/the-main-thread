package dev.signaldesk.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.signaldesk.api.AssistResponse;
import dev.signaldesk.api.Outcome;
import dev.signaldesk.assistant.SignalDeskAssistant;
import dev.signaldesk.trace.AssistTrace;

@ApplicationScoped
public class SignalDeskService {

    @Inject
    SignalDeskAssistant assistant;

    @Inject
    AssistTrace assistTrace;

    public AssistResponse assist(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        assistTrace.reset();
        String answer;
        Outcome outcome = Outcome.OK;

        try {
            answer = assistant.assist(question);
        } catch (Exception e) {
            if (assistTrace.wasToolFailed()) {
                answer = "Runbook lookup failed: " + e.getMessage();
                outcome = Outcome.TOOL_FAILED;
            } else {
                answer = "Assist failed: " + e.getMessage();
                outcome = Outcome.DEGRADED;
            }
        }

        if (assistTrace.wasToolFailed()) {
            outcome = Outcome.TOOL_FAILED;
            if (answer == null || !answer.toUpperCase().contains("ERROR")) {
                answer = "Runbook lookup failed for " + assistTrace.getToolName() + ". " + answer;
            }
        }

        String toolName = assistTrace.wasToolInvoked() ? assistTrace.getToolName() : null;
        return new AssistResponse(answer, assistTrace.wasToolInvoked(), toolName, outcome);
    }
}
