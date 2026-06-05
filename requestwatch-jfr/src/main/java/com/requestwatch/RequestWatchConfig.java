package com.requestwatch;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "requestwatch")
public interface RequestWatchConfig {

    Blocking blocking();

    Allocation allocation();

    Startup startup();

    interface Blocking {

        @WithDefault("150")
        long delayMillis();
    }

    interface Allocation {

        @WithDefault("768")
        int buffers();

        @WithDefault("8192")
        int bufferSizeBytes();
    }

    interface Startup {

        @WithDefault("true")
        boolean enabled();

        @WithDefault("400")
        long delayMillis();

        @WithDefault("256")
        int buffers();

        @WithDefault("16384")
        int bufferSizeBytes();
    }
}
