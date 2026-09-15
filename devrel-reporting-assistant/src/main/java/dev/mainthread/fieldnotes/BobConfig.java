package dev.mainthread.fieldnotes;

import java.time.Duration;
import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "bob")
public interface BobConfig {
    @WithDefault("bob") String binary();
    Optional<String> apiKey();
    Optional<String> keyFile();
    @WithDefault("30m") Duration promptTimeout();
    @WithDefault("5m") Duration formTimeout();
    @WithDefault("http://127.0.0.1:8093") String callbackUrl();
}
