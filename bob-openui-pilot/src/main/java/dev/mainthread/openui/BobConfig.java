package dev.mainthread.openui;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "bob")
public interface BobConfig {
    @WithDefault("bob")
    String binary();

    Optional<String> keyFile();

    Optional<String> apiKey();

    @WithDefault("120s")
    Duration promptTimeout();
}
