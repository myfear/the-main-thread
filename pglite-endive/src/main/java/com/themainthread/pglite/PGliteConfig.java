package com.themainthread.pglite;

import java.nio.file.Path;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "pglite")
public interface PGliteConfig {
    Path dataDirectory();
}
