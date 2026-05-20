package dev.signaldesk.trace;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped record of tool usage and failures for HTTP responses and tutorial narration.
 */
@RequestScoped
public class AssistTrace {

    private boolean toolInvoked;
    private String toolName;
    private boolean toolFailed;

    public void recordTool(String name) {
        this.toolInvoked = true;
        this.toolName = name;
        this.toolFailed = false;
    }

    public void recordToolFailure(String name) {
        this.toolInvoked = true;
        this.toolName = name;
        this.toolFailed = true;
    }

    public boolean wasToolInvoked() {
        return toolInvoked;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean wasToolFailed() {
        return toolFailed;
    }

    public void reset() {
        this.toolInvoked = false;
        this.toolName = null;
        this.toolFailed = false;
    }
}
