package com.themainthread.pglite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

final class BundleFiles {
    private BundleFiles() {
    }

    static void extract(byte[] bundle, Path root) throws IOException {
        try (var compressed = new XZInputStream(new ByteArrayInputStream(bundle));
                var archive = new TarArchiveInputStream(compressed)) {
            TarArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Unsupported bundle entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else if (entry.isLink() || entry.isSymbolicLink()) {
                    Path linked = (entry.isLink() ? root : target.getParent()).resolve(entry.getLinkName()).normalize();
                    if (!linked.startsWith(root) || !Files.isRegularFile(linked)) {
                        throw new IOException("Invalid bundle link: " + entry.getName());
                    }
                    // Materialize timezone aliases as regular files in the isolated filesystem.
                    Files.createDirectories(target.getParent());
                    Files.copy(linked, target);
                } else if (entry.isFile()) {
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target)) {
                        archive.transferTo(output);
                    }
                } else {
                    throw new IOException("Unsupported bundle entry: " + entry.getName());
                }
            }
        }
    }

    static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(resolve(dir));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException("Unsupported data file: " + file);
                }
                Files.copy(file, resolve(file));
                return FileVisitResult.CONTINUE;
            }

            private Path resolve(Path path) {
                Path result = target;
                for (Path component : source.relativize(path)) {
                    result = result.resolve(component.toString());
                }
                return result;
            }
        });
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
