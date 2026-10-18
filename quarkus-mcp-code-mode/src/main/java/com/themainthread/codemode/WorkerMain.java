package com.themainthread.codemode;

import java.io.IOException;
import java.util.Map;

/** One request per disposable JVM. Stdout is reserved for framed IPC. */
public final class WorkerMain {
    private WorkerMain() {
    }

    public static void main(String[] args) throws IOException {
        try {
            var request = Frames.read(System.in);
            if (!"run".equals(request.path("type").asText())) {
                throw new CodeModeFailure("PROTOCOL_ERROR", "Expected a run frame.");
            }
            var bridge = new HostBridge((tool, json) -> {
                try {
                    Frames.write(System.out, Map.of("type", "call", "name", tool, "argumentsJson", json));
                    var reply = Frames.read(System.in);
                    if (!"reply".equals(reply.path("type").asText()) || !reply.path("resultJson").isTextual()) {
                        throw new CodeModeFailure("PROTOCOL_ERROR", "Expected a host reply frame.");
                    }
                    return reply.get("resultJson").asText();
                } catch (IOException e) {
                    throw new CodeModeFailure("HOST_ERROR", "Host callback connection closed.");
                }
            });
            Frames.write(System.out, Map.of("type", "ready"));
            var value = DynamicRuntime.run(new DynamicScript_Factory(), request.path("script").asText(),
                    request.path("inputJson").asText(), bridge, request.path("resultBytes").asInt());
            Frames.write(System.out, Map.of("type", "result", "value", value));
        } catch (CodeModeFailure e) {
            Frames.write(System.out, Map.of("type", "failure", "kind", e.kind, "message", e.getMessage()));
        } catch (RuntimeException e) {
            Frames.write(System.out, Map.of("type", "failure", "kind", "WORKER_ERROR",
                    "message", "QuickJS worker failed."));
        }
    }
}
