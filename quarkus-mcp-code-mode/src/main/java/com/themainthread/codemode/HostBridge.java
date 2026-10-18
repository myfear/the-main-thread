package com.themainthread.codemode;

import java.util.function.BiFunction;

/** Only this string-to-string method is exported into the guest. */
public final class HostBridge {
    private final BiFunction<String, String, String> caller;

    HostBridge(BiFunction<String, String, String> caller) {
        this.caller = caller;
    }

    public String callTool(String toolName, String argumentsJson) {
        try {
            return caller.apply(toolName, argumentsJson);
        } catch (CodeModeFailure e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CodeModeFailure("HOST_ERROR", "Java host callback failed.");
        }
    }
}
