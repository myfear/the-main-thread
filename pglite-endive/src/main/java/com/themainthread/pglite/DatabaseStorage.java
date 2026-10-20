package com.themainthread.pglite;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.UUID;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

final class DatabaseStorage implements AutoCloseable {
    final FileSystem fileSystem;
    final Path guestData;
    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;

    DatabaseStorage(Path directory, byte[] bundle) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve("database.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("Database directory is already open: " + directory, e);
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("Database directory is already open: " + directory);
        }
        lock = acquired;
        fileSystem = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "posix", "unix")
                .build());
        guestData = fileSystem.getPath("/tmp/pglite/base");
        try {
            BundleFiles.extract(bundle, fileSystem.getPath("/"));
            Files.createDirectories(fileSystem.getPath("/dev"));
            byte[] entropy = new byte[65536];
            new SecureRandom().nextBytes(entropy);
            Files.write(fileSystem.getPath("/dev/urandom"), entropy);
            Path current = this.directory.resolve("CURRENT");
            if (Files.exists(current)) {
                String version = Files.readString(current).strip();
                if (!version.matches("[0-9a-f-]{36}")) {
                    throw new IOException("Invalid PGlite snapshot pointer");
                }
                BundleFiles.copyTree(this.directory.resolve("snapshots").resolve(version), guestData);
                if (!Files.isRegularFile(guestData.resolve("PG_VERSION"))) {
                    throw new IOException("Restored PGlite snapshot has no PG_VERSION");
                }
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    void persist() throws IOException {
        String version = UUID.randomUUID().toString();
        Path snapshot = directory.resolve("snapshots").resolve(version);
        BundleFiles.copyTree(guestData, snapshot);
        Path pointer = directory.resolve("CURRENT.next");
        Files.writeString(pointer, version + "\n");
        Files.move(pointer, directory.resolve("CURRENT"), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        // The new snapshot is complete before the previous snapshot is removed.
        try (var versions = Files.list(directory.resolve("snapshots"))) {
            for (Path previous : versions.toList()) {
                if (!previous.equals(snapshot)) {
                    BundleFiles.deleteTree(previous);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            fileSystem.close();
        } finally {
            try {
                lock.release();
            } finally {
                lockChannel.close();
            }
        }
    }
}
