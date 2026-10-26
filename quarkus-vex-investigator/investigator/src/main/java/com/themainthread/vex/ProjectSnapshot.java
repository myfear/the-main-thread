package com.themainthread.vex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;

final class ProjectSnapshot {
    static ObjectNode capture(Path project, Path sbom, Path dependencySbom) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        add(hashes, project.resolve("pom.xml"));
        Path parentPom = project.getParent().resolve("pom.xml");
        if (Files.isRegularFile(parentPom)) add(hashes, parentPom);
        addTree(hashes, project.resolve("src"));
        addTree(hashes, project.resolve(".mvn"));
        Path distribution = project.resolve("target/quarkus-app");
        EvidenceHasher.require(Files.isRegularFile(distribution.resolve("quarkus-run.jar")), "Build the Fast JAR first");
        checkDistribution(sbom, distribution);
        addTree(hashes, distribution);
        add(hashes, sbom);
        if (dependencySbom != null) add(hashes, dependencySbom);
        return EvidenceHasher.JSON.valueToTree(hashes);
    }

    private static void checkDistribution(Path sbom, Path distribution) throws IOException {
        var document = EvidenceHasher.read(sbom);
        var entries = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        entries.add(document.path("metadata").path("component"));
        document.path("components").forEach(entries::add);
        for (var component : entries) {
            if (!component.equals(entries.getFirst()) && !component.path("purl").asText().startsWith("pkg:maven/")) continue;
            String expected = "";
            for (var digest : component.path("hashes")) if (digest.path("alg").asText().equals("SHA-256")) expected = digest.path("content").asText();
            EvidenceHasher.require(expected.matches("[0-9a-f]{64}"), "Distribution component requires SHA-256");
            EvidenceHasher.require(component.path("evidence").path("occurrences").isArray()
                    && !component.path("evidence").path("occurrences").isEmpty(), "Distribution component lacks artifact locations");
            for (var occurrence : component.path("evidence").path("occurrences")) {
                Path file = EvidenceHasher.safe(distribution, occurrence.path("location").asText());
                EvidenceHasher.require(Files.isRegularFile(file) && EvidenceHasher.hash(file).equals(expected), "SBOM does not match the packaged artifact");
            }
        }
    }

    private static void addTree(Map<String, String> hashes, Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            for (Path file : files.toList()) {
                EvidenceHasher.require(!Files.isSymbolicLink(file), "Symlinks are unsupported in target inputs");
                if (Files.isRegularFile(file)) add(hashes, file);
            }
        }
    }

    private static void add(Map<String, String> hashes, Path file) throws IOException {
        EvidenceHasher.require(!Files.isSymbolicLink(file), "Linked target input");
        hashes.put(file.toRealPath().toString(), EvidenceHasher.hash(file));
    }

    static String git(Path project, String... arguments) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.add("--no-optional-locks");
        command.add("-C");
        command.add(project.toString());
        command.addAll(java.util.List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git timed out");
            }
            String value = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) throw new IOException("Git identity unavailable");
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git interrupted", e);
        }
    }
}
