package com.ibm.developer.shieldstral.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "safety")
public interface SafetyPoliciesConfig {

    @WithName("public-support")
    Policy publicSupport();

    @WithName("security-research")
    Policy securityResearch();

    interface Policy {

        String instruction();

        @WithName("input-query")
        String inputQuery();

        @WithName("output-query")
        String outputQuery();

        @WithDefault("0.5")
        double threshold();

        @WithName("fail-closed")
        @WithDefault("true")
        boolean failClosed();
    }
}
