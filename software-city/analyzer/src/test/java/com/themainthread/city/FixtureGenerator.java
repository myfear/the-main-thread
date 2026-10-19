package com.themainthread.city;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Recreates only this module's disposable target/fixture-repo directory. */
public final class FixtureGenerator {
    private FixtureGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path target = Path.of("target").toAbsolutePath().normalize();
        Path root = target.resolve("fixture-repo");
        TestRepository.delete(root);
        try (var repository = new TestRepository(root)) {
            repository.copyFixture();
            String commit = repository.commit();
            Path output = target.resolve("fixture.snapshot.json");
            var document = new GitSnapshotAnalyzer().analyze(root, commit, output,
                    java.util.List.of(), java.util.List.of(), true);
            new CityJson(new ObjectMapper()).write(document, output);
            Files.writeString(target.resolve("fixture-commit.txt"), commit + "\n");
        }
    }
}
