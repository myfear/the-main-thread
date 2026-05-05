package dev.conduit.workflow.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "conduit.mcp")
public interface ConduitMcpConfig {

    String serverUrl();
}