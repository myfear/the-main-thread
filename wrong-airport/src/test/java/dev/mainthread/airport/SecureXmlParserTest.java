// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SecureXmlParserTest {
    private final SecureXmlParser parser = new SecureXmlParser();

    @Test
    void rejectsDoctype() {
        rejects(Fixtures.bytes("xxentity"));
    }

    @Test
    void rejectsMalformedAndEmptyDocuments() {
        rejects("<rdf:RDF>".getBytes(StandardCharsets.UTF_8));
        rejects(new byte[0]);
    }

    @Test
    void limitsNestingDepth() {
        rejects(("<a>".repeat(65) + "</a>".repeat(65)).getBytes(StandardCharsets.UTF_8));
    }

    private void rejects(byte[] bytes) {
        assertEquals(AirportFailure.Kind.MALFORMED_XML,
                assertThrows(AirportFailure.class, () -> parser.parse(bytes)).kind());
    }
}
