package dev.cleardesk.api;

/**
 * Body for {@code POST /clear-desk/chat}.
 */
public class ClearDeskChatRequest {

    /**
     * User message to the supervisor.
     */
    public String prompt;

    /**
     * When true, use filesystem-backed Skills (tool mode). When false, use the vague baseline system prompt only.
     */
    public boolean skillsEnabled = true;

    /**
     * Optional chat memory id for repeatable multi-step conversations.
     */
    public String memoryId;
}
