// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class Fixtures {
    private Fixtures() {
    }

    static byte[] bytes(String name) {
        try (var input = Fixtures.class.getResourceAsStream("/rdf/" + name + ".rdf")) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test fixture: " + name);
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }
}
