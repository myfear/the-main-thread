package com.themainthread.codemode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class GatewayMain implements QuarkusApplication {
    private static GatewayInput input;
    private final GatewayTools tools;

    public GatewayMain(GatewayTools tools) {
        this.tools = tools;
    }

    public static void main(String[] args) {
        input = new GatewayInput(System.in, 65_536);
        System.setIn(input);
        Quarkus.run(GatewayMain.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        try {
            tools.start();
            // The extension reads stdin during boot. Admit requests only after registration and demo setup.
            input.open();
            Quarkus.waitForExit();
        } finally {
            tools.close();
        }
        return 0;
    }

    static List<String> upstreamCommand(GatewayConfig config) throws Exception {
        if (config.upstreamJar().isPresent()) {
            Path path = config.upstreamJar().get().toRealPath();
            String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
            if (!actual.equals("ea9511f09ba197b094692b1622cb95be6e94e5e1fbc3d8e2e732b834ef8615a9")) {
                throw new IllegalArgumentException("Pinned upstream JAR checksum mismatch");
            }
            return List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Xmx256m", "-jar", path.toString());
        }
        Path jbang = config.jbang().orElseThrow(() -> new IllegalArgumentException("Set gateway.jbang or gateway.upstream-jar"));
        if (!jbang.isAbsolute() || !Files.isExecutable(jbang)) {
            throw new IllegalArgumentException("gateway.jbang must be an absolute executable path");
        }
        return List.of(jbang.toString(), "io.quarkus:quarkus-agent-mcp:1.0.10:runner");
    }

    static Map<String, String> upstreamEnvironment() {
        var environment = new HashMap<String, String>();
        // Only trusted toolchain settings go to upstream. Workers receive an empty environment.
        for (String name : List.of("HOME", "JAVA_HOME", "PATH")) {
            String value = System.getenv(name);
            if (value != null) {
                environment.put(name, value);
            }
        }
        environment.put("JAVA_HOME", System.getProperty("java.home"));
        environment.put("QUARKUS_LOG_CONSOLE_STDERR", "true");
        environment.put("QUARKUS_BANNER_ENABLED", "false");
        environment.put("AGENT_MCP_LOG_ENABLED", "false");
        return Map.copyOf(environment);
    }

}
