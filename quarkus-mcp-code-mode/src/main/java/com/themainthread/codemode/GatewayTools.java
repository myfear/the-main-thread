package com.themainthread.codemode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

@Singleton
public class GatewayTools implements AutoCloseable {
    private final GatewayConfig config;
    private final ToolManager toolManager;
    private GatewayExecutor executor;

    public GatewayTools(GatewayConfig config, ToolManager toolManager) {
        this.config = config;
        this.toolManager = toolManager;
    }

    void start() throws Exception {
        Path project = config.project().toRealPath();
        Catalog catalog = Catalog.pinned();
        catalog.write(config.session().resolve("sdk"));
        var defaults = ExecutionLimits.defaults();
        var limits = new ExecutionLimits(defaults.scriptBytes(), defaults.inputBytes(), defaults.calls(),
                Duration.ofMillis(config.toolTimeoutMs()), Duration.ofMillis(config.executionTimeoutMs()),
                defaults.resultBytes(), defaults.upstreamBytes());
        executor = new GatewayExecutor(limits, catalog, new Policy(project, catalog), new Trace(config.session()),
                GatewayMain.upstreamCommand(config), GatewayMain.upstreamEnvironment());
        if (config.startDemo()) {
            executor.startFixture(config.demoPort());
        }
        toolManager.newTool("code")
                .setDescription(catalog.description())
                .addArgument("script", "Synchronous JavaScript function body", true, String.class)
                .setInputSchema(Map.of("type", "object", "properties", Map.of("script", Map.of("type", "string")),
                        "required", List.of("script"), "additionalProperties", false))
                .setHandler(this::execute, true)
                .register();
    }

    private ToolResponse execute(ToolManager.ToolArguments request) {
        Object id = request.requestId().value();
        var cancellation = request.cancellation();
        cancellation.onCancelled(reason -> executor.cancel(id));
        Map<String, Object> arguments = request.args();
        ExecutionResult result;
        if (arguments == null || arguments.size() != 1 || !(arguments.get("script") instanceof String)) {
            result = GatewayExecutor.failure("INVALID_ARGUMENTS", "code requires exactly one string field: script.");
        } else {
            result = executor.execute(id, (String) arguments.get("script"), () -> cancellation.check().isRequested());
        }
        Object envelope = Json.MAPPER.convertValue(result, Object.class);
        return new ToolResponse(!result.ok(), List.of(new TextContent(Json.MAPPER.valueToTree(result).toString())),
                envelope, null);
    }

    @Override
    @PreDestroy
    public void close() {
        if (executor != null) {
            executor.close();
        }
    }
}
