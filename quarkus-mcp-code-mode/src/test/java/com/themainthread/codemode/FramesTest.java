package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FramesTest {
    @Test
    void framePreservesUnicodeAndEmbeddedNewlines() throws Exception {
        var output = new ByteArrayOutputStream();
        Frames.write(output, Map.of("text", "Grüße 🌍\nquoted \"\\"));
        assertEquals("Grüße 🌍\nquoted \"\\", Frames.read(new ByteArrayInputStream(output.toByteArray())).get("text").asText());
    }

    @Test
    void oversizedAndNegativeLengthsAreRejectedBeforeReadingPayload() throws Exception {
        for (int size : new int[] {-1, 0, Frames.MAX_BYTES + 1}) {
            var output = new ByteArrayOutputStream();
            new DataOutputStream(output).writeInt(size);
            var failure = assertThrows(CodeModeFailure.class, () -> Frames.read(new ByteArrayInputStream(output.toByteArray())));
            assertEquals("FRAME_LIMIT", failure.kind);
        }
    }

    @Test
    void incompletePayloadAndTrailingJsonAreRejected() throws Exception {
        var output = new ByteArrayOutputStream();
        new DataOutputStream(output).writeInt(10);
        output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(EOFException.class, () -> Frames.read(new ByteArrayInputStream(output.toByteArray())));
        assertThrows(CodeModeFailure.class, () -> Json.parse("{} {}"));
        assertThrows(CodeModeFailure.class, () -> Json.parse("[".repeat(65) + "0" + "]".repeat(65)));
    }
}
