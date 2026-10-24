package dev.mainthread.pqc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class LabFiles {
    private LabFiles() {
    }

    static byte[] readBounded(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new IllegalArgumentException("Input exceeds the " + limit + " byte lab limit.");
            }
            return bytes;
        }
    }

    // All generated files, including recovered plaintext, get owner-only POSIX access.
    static boolean writeNew(Path path, byte[] bytes) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        Files.createDirectories(parent);
        boolean posix = Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class);
        FileAttribute<?>[] attributes = posix
                ? new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")) }
                : new FileAttribute<?>[0];
        SeekableByteChannel channel = Files.newByteChannel(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), attributes);
        try (channel) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        return posix;
    }
}
