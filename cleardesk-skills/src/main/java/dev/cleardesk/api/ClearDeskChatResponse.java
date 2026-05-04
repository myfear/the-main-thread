package dev.cleardesk.api;

/**
 * Response from {@code POST /clear-desk/chat}.
 */
public class ClearDeskChatResponse {

    public String reply;

    public String memoryId;

    public boolean skillsEnabled;

    /**
     * Specialist picked by delegate tools after routing (best-effort — null if the model never called a route tool).
     */
    public String routedSpecialist;

    public ClearDeskChatResponse() {
    }

    public ClearDeskChatResponse(String reply, String memoryId, boolean skillsEnabled, String routedSpecialist) {
        this.reply = reply;
        this.memoryId = memoryId;
        this.skillsEnabled = skillsEnabled;
        this.routedSpecialist = routedSpecialist;
    }
}
