package com.themainthread.mavenlab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class RunRecord {
    static final List<String> SCENARIOS = List.of(
            "validation", "classpath", "reproducibility", "consumer_pom", "credentials");
    private static final String SEAL = "completed.properties";
    private final Path directory;
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private final Map<String, Object> scenarios = new LinkedHashMap<>();
    private final List<String> requested;
    private boolean finished;

    RunRecord(Path runs, List<String> requested, Map<String, String> runtime,
            Map<String, String> distributions, String runnerHash) throws IOException {
        if (requested.isEmpty() || !SCENARIOS.containsAll(requested)
                || requested.stream().distinct().count() != requested.size()) {
            throw new IllegalArgumentException("Choose distinct known scenarios");
        }
        this.requested = List.copyOf(requested);
        String timestamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String id = timestamp + "-" + UUID.randomUUID();
        Files.createDirectories(runs);
        directory = Files.createDirectory(runs.resolve(id));
        metadata.put("schema_version", 1);
        metadata.put("run_id", id);
        metadata.put("scope", isFull() ? "full" : "partial");
        metadata.put("requested_scenarios", this.requested);
        metadata.put("started_at", Instant.now().toString());
        metadata.put("runtime", Map.copyOf(runtime));
        metadata.put("distributions_sha512", Map.copyOf(distributions));
        metadata.put("runner_sha256", runnerHash);
        metadata.put("status", "running");
        write();
    }

    Path directory() {
        return directory;
    }

    String id() {
        return directory.getFileName().toString();
    }

    boolean isFull() {
        return requested.size() == SCENARIOS.size() && requested.containsAll(SCENARIOS);
    }

    void start(String name) throws IOException {
        requireRunning();
        if (!requested.contains(name) || scenarios.containsKey(name)) {
            throw new IllegalStateException("Unexpected scenario: " + name);
        }
        scenarios.put(name, Map.of("status", "running", "started_at", Instant.now().toString()));
        write();
    }

    void pass(String name, Map<String, Object> results) throws IOException {
        requireRunning();
        Map<String, Object> entry = entry(name);
        if (!"running".equals(entry.get("status"))) {
            throw new IllegalStateException("Scenario is not running: " + name);
        }
        entry.put("status", "passed");
        entry.put("finished_at", Instant.now().toString());
        entry.put("results", new LinkedHashMap<>(results));
        scenarios.put(name, entry);
        write();
    }

    void fail(String name, Exception error) throws IOException {
        requireRunning();
        Map<String, Object> entry = entry(name);
        entry.put("status", "failed");
        entry.put("finished_at", Instant.now().toString());
        entry.put("error", error.toString());
        scenarios.put(name, entry);
        fail(error);
    }

    void fail(Exception error) throws IOException {
        if (finished) {
            return;
        }
        metadata.put("status", "failed");
        metadata.put("finished_at", Instant.now().toString());
        metadata.put("error", error.toString());
        finished = true;
        write();
    }

    void finish() throws IOException {
        requireRunning();
        for (String name : requested) {
            if (!"passed".equals(entry(name).get("status"))) {
                throw new IllegalStateException("Scenario did not pass: " + name);
            }
        }
        metadata.put("status", "passed");
        metadata.put("finished_at", Instant.now().toString());
        write();
        Properties seal = new Properties();
        seal.setProperty("run.id", id());
        seal.setProperty("run.scope", isFull() ? "full" : "partial");
        seal.setProperty("run.status", "passed");
        seal.setProperty("run.scenarios", String.join(",", requested));
        for (String name : requested) {
            seal.setProperty("scenario." + name + ".status", "passed");
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    seal.setProperty("sha256." + file.getFileName(), hash(file));
                }
            }
        }
        Path pending = directory.resolve("completed.properties.tmp");
        try (var writer = Files.newBufferedWriter(pending, StandardCharsets.UTF_8)) {
            seal.store(writer, "Completed verification record; timestamps are in summary.json (UTC)");
        }
        Files.move(pending, directory.resolve(SEAL), StandardCopyOption.ATOMIC_MOVE);
        finished = true;
    }

    Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>(metadata);
        document.put("scenarios", new LinkedHashMap<>(scenarios));
        return document;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entry(String name) {
        if (!(scenarios.get(name) instanceof Map<?, ?> entry)) {
            throw new IllegalStateException("Scenario has not started: " + name);
        }
        return new LinkedHashMap<>((Map<String, Object>) entry);
    }

    private void requireRunning() {
        if (finished) {
            throw new IllegalStateException("This run is already finished");
        }
    }

    private void write() throws IOException {
        Path temporary = directory.resolve("summary.json.tmp");
        Files.writeString(temporary, Json.encode(document()) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary, directory.resolve("summary.json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, directory.resolve("summary.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void snapshot(Path source, Path destination) throws IOException {
        Properties seal = new Properties();
        Path manifest = source.resolve(SEAL);
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected run has not completed successfully");
        }
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            seal.load(reader);
        }
        if (!source.getFileName().toString().equals(seal.getProperty("run.id"))
                || !"full".equals(seal.getProperty("run.scope"))
                || !"passed".equals(seal.getProperty("run.status"))
                || !String.join(",", SCENARIOS).equals(seal.getProperty("run.scenarios"))) {
            throw new IOException("Snapshots require a successful full run, not a partial or failed run");
        }
        for (String name : SCENARIOS) {
            if (!"passed".equals(seal.getProperty("scenario." + name + ".status"))) {
                throw new IOException("Missing successful scenario: " + name);
            }
        }
        List<Path> files = new ArrayList<>();
        if (!seal.containsKey("sha256.summary.json")) {
            throw new IOException("Missing summary checksum");
        }
        for (String key : seal.stringPropertyNames().stream().sorted().toList()) {
            if (key.startsWith("sha256.")) {
                String name = key.substring("sha256.".length());
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                    throw new IOException("Invalid record filename: " + name);
                }
                Path file = source.resolve(name);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !hash(file).equals(seal.getProperty(key))) {
                    throw new IOException("Record changed after completion: " + name);
                }
                files.add(file);
            }
        }
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new IOException("Snapshot destination already exists: " + target);
        }
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".maven-lab-snapshot-");
        try {
            for (Path file : files) {
                Files.copy(file, staging.resolve(file.getFileName()));
            }
            Files.copy(manifest, staging.resolve(SEAL));
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (Files.exists(staging)) {
                MavenTools.deleteTree(staging);
            }
        }
    }

    static String hash(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
