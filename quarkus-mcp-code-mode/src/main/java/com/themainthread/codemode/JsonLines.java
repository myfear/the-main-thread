package com.themainthread.codemode;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;

/** Bounded newline framing shared by the stdio transport and its test server. */
final class JsonLines {
    private JsonLines() {
    }

    static JsonNode read(InputStream input, int maximumBytes) throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (int b; (b = input.read()) != -1;) {
            if (b == '\n') {
                return Json.parse(bytes.toString(StandardCharsets.UTF_8));
            }
            if (bytes.size() >= maximumBytes) {
                throw new CodeModeFailure("UPSTREAM_LIMIT", "Upstream response exceeds the permitted size.");
            }
            bytes.write(b);
        }
        throw new EOFException("Upstream closed stdout");
    }

    static void write(OutputStream output, Object value) throws IOException {
        output.write(Json.bytes(value));
        output.write('\n');
        output.flush();
    }
}
