package com.themainthread.codemode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Two files, at most about 1 MiB each. No source, argument values, tool output, or exception messages. */
final class Trace {
    private final Path file;

    Trace(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("gateway.jsonl");
    }

    synchronized void write(Map<String, ?> event) {
        try {
            byte[] line = Json.bytes(event);
            if (line.length > 4096) {
                throw new IOException("Trace record limit");
            }
            if (Files.exists(file) && Files.size(file) + line.length > 1_048_576) {
                Files.move(file, file.resolveSibling("gateway.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(file, "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new CodeModeFailure("TRACE_ERROR", "Could not persist bounded execution evidence.");
        }
    }
}
