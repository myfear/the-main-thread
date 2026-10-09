package dev.themainthread.wallet;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "pass")
public interface PassConfig {

    String typeIdentifier();

    String teamIdentifier();

    String orgName();
}
