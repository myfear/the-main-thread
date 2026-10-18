package com.themainthread.codemode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;

/** Private worker IPC: big-endian signed int length, then exactly that many UTF-8 JSON bytes. */
final class Frames {
    static final int MAX_BYTES = 128 * 1024;

    private Frames() {
    }

    static JsonNode read(InputStream source) throws IOException {
        var input = new DataInputStream(source);
        int length = input.readInt();
        if (length < 1 || length > MAX_BYTES) {
            throw new CodeModeFailure("FRAME_LIMIT", "Worker frame exceeds the permitted size.");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Incomplete worker frame");
        }
        return Json.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    static void write(OutputStream destination, Object message) throws IOException {
        byte[] bytes = Json.bytes(message);
        if (bytes.length > MAX_BYTES) {
            throw new CodeModeFailure("FRAME_LIMIT", "Worker frame exceeds the permitted size.");
        }
        var output = new DataOutputStream(destination);
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }
}
