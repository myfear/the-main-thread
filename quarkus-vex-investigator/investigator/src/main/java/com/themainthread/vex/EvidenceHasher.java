package com.themainthread.vex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class EvidenceHasher {
    static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static final int MAX_JSON_BYTES = 8 * 1024 * 1024;

    static String hash(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Path safe(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.contains("\\") || relative.contains(":"))
            throw new CaseException(4, "Invalid evidence path");
        Path part = Path.of(relative);
        if (part.isAbsolute()) throw new CaseException(4, "Absolute evidence path");
        for (Path element : part) {
            if (element.toString().equals("..") || element.toString().equals("."))
                throw new CaseException(4, "Path traversal");
        }
        Path base = root.toRealPath();
        Path candidate = base;
        for (Path element : part) {
            candidate = candidate.resolve(element);
            if (Files.isSymbolicLink(candidate)) throw new CaseException(4, "Symlink in case path");
        }
        if (!candidate.normalize().startsWith(base)) throw new CaseException(4, "Outside case directory");
        return candidate;
    }

    static JsonNode read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_JSON_BYTES)
            throw new CaseException(4, "Missing, linked, or oversized JSON: " + path.getFileName());
        JsonNode node = JSON.readTree(path.toFile());
        if (node == null || !node.isObject()) throw new CaseException(2, "Expected a JSON object");
        return node;
    }

    static void write(Path path, JsonNode value) throws IOException {
        writeText(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
    }

    static void writeText(Path path, String value) throws IOException {
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent()))
            throw new CaseException(4, "Refusing linked output");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".vex-", ".tmp");
        try {
            Files.writeString(temporary, value);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static ObjectNode object() { return JSON.createObjectNode(); }

    static void require(boolean condition, String message) {
        if (!condition) throw new CaseException(4, message);
    }
}
