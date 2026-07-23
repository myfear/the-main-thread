package com.themainthread.releasegate;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "release-gate")
interface ReleaseGateConfig {

    @WithDefault("monolith")
    String nodeId();
}
