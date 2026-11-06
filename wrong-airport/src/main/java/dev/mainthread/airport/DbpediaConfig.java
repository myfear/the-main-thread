// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "mainthread.dbpedia")
public interface DbpediaConfig {
    @WithDefault("4194304")
    int maxBytes();

    @WithDefault("5s")
    Duration bodyTimeout();
}
