package dev.mainthread.bobweb.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "bob")
public interface BobConfig {

    @WithDefault("bob")
    String binary();

    @WithDefault("acp,--disable-mcp,--disable-subagents")
    List<String> arguments();

    Optional<String> apiKey();

    @WithDefault(".")
    String workspaceRoot();

    @WithDefault("30s")
    Duration requestTimeout();

    @WithDefault("10m")
    Duration promptTimeout();

    @WithDefault("2m")
    Duration permissionTimeout();

    @WithDefault("4")
    int maxConversations();
}
