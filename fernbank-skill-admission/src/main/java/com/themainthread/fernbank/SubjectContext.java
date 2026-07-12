package com.themainthread.fernbank;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubjectContext(
        @JsonProperty("user_id") String userId,
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("session_id") String sessionId,
        String team) {
}
