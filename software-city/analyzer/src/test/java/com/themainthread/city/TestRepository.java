package com.themainthread.city;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

final class TestRepository implements AutoCloseable {
    final Path root;
    final Git git;
    private int commits;

    TestRepository(Path root) throws GitAPIException, IOException {
        this.root = root;
        Files.createDirectories(root);
        git = Git.init().setDirectory(root.toFile()).setInitialBranch("main").call();
        var config = git.getRepository().getConfig();
        config.setString("core", null, "autocrlf", "false");
        config.setBoolean("core", null, "filemode", false);
        config.save();
    }

    void write(String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    String commit() throws GitAPIException {
        var identity = new PersonIdent("Software City Fixture", "fixture@example.invalid",
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(commits++), ZoneOffset.UTC);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage("Fixture " + commits).setAuthor(identity).setCommitter(identity)
                .setSign(false).setNoVerify(true).call().getName();
    }

    void copyFixture() throws IOException {
        Path source = Path.of("../fixtures/source");
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                write(source.relativize(file).toString(), Files.readString(file));
            }
        }
    }

    static void delete(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Override
    public void close() {
        git.close();
    }
}
