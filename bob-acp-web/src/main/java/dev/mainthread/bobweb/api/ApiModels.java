package dev.mainthread.bobweb.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiModels {

    private ApiModels() {
    }

    public record CreateConversationRequest(String workspace) {
    }

    public record SendMessageRequest(String prompt) {
    }

    public record ChangeModeRequest(String modeId) {
    }

    public record PermissionDecisionRequest(String optionId) {
    }

    public record ActionAccepted(String status) {
    }

    public record ApiError(String message) {
    }

    public record ModeView(String id, String name, String description) {
    }

    public record CommandView(String name, String description, Object input) {
    }

    public record ConversationSummary(String id, String agentSessionId, String title, String workspace, String status,
            String currentMode, Instant updatedAt) {
    }

    public record ConversationView(String id, String agentSessionId, String title, String workspace, String status,
            String currentMode, Instant updatedAt, String agentName, String agentVersion, List<ModeView> modes,
            List<CommandView> commands, List<UiEvent> events) {
    }

    public record UiEvent(long sequence, String type, Instant at, Map<String, Object> data) {
    }
}
