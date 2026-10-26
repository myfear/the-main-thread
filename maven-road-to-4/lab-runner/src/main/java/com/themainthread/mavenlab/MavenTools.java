package com.themainthread.mavenlab;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

final class MavenTools {
    static final List<String> VERSIONS = List.of("3.9.16", "3.10.0", "4.0.0-rc-7");
    private final Path root;
    private final Path lab;
    private final Map<String, String> checksums = new LinkedHashMap<>();
    private final Set<String> installed = new HashSet<>();

    MavenTools(Path root) throws IOException {
        this.root = root;
        lab = root.resolve(".lab");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("config/maven-distributions.properties"))) {
            properties.load(reader);
        }
        for (String version : VERSIONS) {
            String checksum = properties.getProperty(version);
            if (checksum == null || !checksum.matches("[a-f0-9]{128}")) {
                throw new IOException("Missing SHA-512 for Maven " + version);
            }
            checksums.put(version, checksum);
        }
    }

    Map<String, String> checksums() {
        return Map.copyOf(checksums);
    }

    void install(String version) throws Exception {
        if (!checksums.containsKey(version)) {
            throw new IllegalArgumentException("Supported Maven versions: " + String.join(", ", VERSIONS));
        }
        if (installed.contains(version)) {
            return;
        }
        Path tools = Files.createDirectories(lab.resolve("tools"));
        Path archive = tools.resolve("apache-maven-" + version + "-bin.zip");
        if (!Files.isRegularFile(archive)) {
            IO.println("Downloading Maven " + version);
            URI url = URI.create("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/"
                    + version + "/" + archive.getFileName());
            Path temporary = archive.resolveSibling(archive.getFileName() + ".part");
            try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30)).build()) {
                HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(3)).build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
                if (response.statusCode() != 200) {
                    throw new IOException("Distribution download returned HTTP " + response.statusCode());
                }
                checkArchive(temporary, checksums.get(version));
                Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        checkArchive(archive, checksums.get(version));
        Path distribution = tools.resolve("apache-maven-" + version);
        Path executable = distribution.resolve("bin/mvn");
        if (!Files.isRegularFile(executable)) {
            Path staging = Files.createTempDirectory(tools, ".extract-");
            try {
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                    for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        Path destination = staging.resolve(entry.getName()).normalize();
                        if (!destination.startsWith(staging)) {
                            throw new IOException("Unsafe distribution entry: " + entry.getName());
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(zip, destination);
                        }
                    }
                }
                if (Files.exists(distribution)) {
                    deleteTree(distribution);
                }
                Files.move(staging.resolve(distribution.getFileName()), distribution);
            } finally {
                deleteTree(staging);
            }
        }
        if (!executable.toFile().setExecutable(true, true)) {
            throw new IOException("Cannot make Maven executable: " + executable);
        }
        installed.add(version);
    }

    private static void checkArchive(Path archive, String checksum) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(archive));
            if (!HexFormat.of().formatHex(digest).equals(checksum)) {
                throw new IOException("Distribution checksum mismatch: " + archive);
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    List<String> command(String version, List<String> arguments) throws Exception {
        return command(version, arguments, root.resolve("config/settings.xml"));
    }

    private List<String> command(String version, List<String> arguments, Path settings) throws Exception {
        install(version);
        List<String> command = new ArrayList<>(List.of(
                lab.resolve("tools/apache-maven-" + version + "/bin/mvn").toString(),
                "--batch-mode", "--no-transfer-progress", "-Dstyle.color=never",
                "-s", settings.toString(),
                "-gs", root.resolve("config/settings.xml").toString(),
                "-Dmaven.repo.local=" + lab.resolve("repository")));
        command.addAll(arguments);
        return command;
    }

    Result maven(String version, Path project, List<String> arguments, Path log, Integer expected)
            throws Exception {
        return maven(version, project, arguments, log, expected, root.resolve("config/settings.xml"));
    }

    Result maven(String version, Path project, List<String> arguments, Path log, Integer expected, Path settings)
            throws Exception {
        List<String> options = new ArrayList<>(List.of("-f", project.resolve("pom.xml").toString()));
        options.addAll(arguments);
        Result result = execute(command(version, options, settings), log);
        IO.println(log.getFileName() + ": exit " + result.exit());
        if (expected != null && result.exit() != expected) {
            throw new IOException("Expected exit " + expected + ", got " + result.exit() + "; read " + log);
        }
        return result;
    }

    int interactive(String version, List<String> arguments) throws Exception {
        Process process = process(command(version, arguments)).inheritIO().start();
        return process.waitFor();
    }

    Result execute(List<String> command, Path log) throws Exception {
        Files.createDirectories(log.getParent());
        Files.writeString(log, "Command: " + String.join(" ", command) + "\n", StandardCharsets.UTF_8);
        Process process = process(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        try {
            if (!process.waitFor(240, TimeUnit.SECONDS)) {
                throw new IOException("Process timed out; read " + log);
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(handle -> handle.destroyForcibly());
                process.destroyForcibly();
                process.waitFor();
            }
        }
        return new Result(process.exitValue(), Files.readString(log));
    }

    private ProcessBuilder process(List<String> command) throws IOException {
        Files.createDirectories(lab.resolve("home"));
        ProcessBuilder process = new ProcessBuilder(command).directory(root.toFile());
        for (String name : List.of("MAVEN_ARGS", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) {
            process.environment().remove(name);
        }
        process.environment().put("JAVA_HOME", System.getProperty("java.home"));
        process.environment().put("JAVA_TOOL_OPTIONS", "-Duser.home=\"" + lab.resolve("home") + "\"");
        return process;
    }

    static String javaCommand() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    record Result(int exit, String output) {
    }
}
