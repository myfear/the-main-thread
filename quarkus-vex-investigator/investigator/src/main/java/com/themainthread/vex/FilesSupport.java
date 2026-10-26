package com.themainthread.vex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class FilesSupport {
    static void writeBytes(Path path, byte[] bytes) throws IOException {
        if (Files.exists(path)) throw new CaseException(4, "Refusing evidence overwrite");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".input-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
}
