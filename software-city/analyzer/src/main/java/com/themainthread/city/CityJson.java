package com.themainthread.city;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CityJson {
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public CityJson(ObjectMapper mapper) {
        this.mapper = mapper.copy().setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .setDefaultPrettyPrinter(new DefaultPrettyPrinter()
                        .withObjectIndenter(new DefaultIndenter("  ", "\n")));
        try (InputStream input = CityJson.class.getResourceAsStream("/schemas/software-city-v1.schema.json")) {
            if (input == null) {
                throw new IllegalStateException("Packaged Software City schema is missing.");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load the packaged schema.", e);
        }
    }

    public byte[] serialize(CityDocument document) throws IOException {
        validate(document);
        return (mapper.writeValueAsString(document) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public void write(CityDocument document, Path output) throws IOException {
        byte[] bytes = serialize(document);
        Path destination = output.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".software-city-", ".json");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void validate(CityDocument document) throws IOException {
        Set<String> errors = new TreeSet<>();
        schema.validate(mapper.valueToTree(document)).forEach(error -> errors.add(error.getMessage()));
        if (!errors.isEmpty()) {
            throw new IOException("JSON schema validation failed: " + String.join("; ", errors));
        }
        Set<String> ids = new HashSet<>();
        int baseCount = 0;
        int headCount = 0;
        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        for (var type : document.types()) {
            if (!ids.add(type.id()) || !type.id().equals(type.module() + "::" + type.qualifiedName())
                    || !type.qualifiedName().equals(type.packageName().isEmpty() ? type.typeName()
                            : type.packageName() + "." + type.typeName())) {
                throw new IOException("Duplicate or inconsistent type identity: " + type.id());
            }
            boolean base = checkSide(type.source().base(), type.metrics().base(), type.typeKind().base());
            boolean head = checkSide(type.source().head(), type.metrics().head(), type.typeKind().head());
            if (!base && !head) {
                throw new IOException("A type must exist in at least one revision.");
            }
            baseCount += base ? 1 : 0;
            headCount += head ? 1 : 0;
            switch (type.change().status()) {
                case "added" -> {
                    require(!base && head, "Added types require only head data.");
                    added++;
                }
                case "removed" -> {
                    require(base && !head, "Removed types require only base data.");
                    removed++;
                }
                case "modified" -> {
                    require(base && head, "Modified types require both revisions.");
                    modified++;
                }
                case "unchanged" -> {
                    require(head && (document.mode().equals("snapshot") || base),
                            "Unchanged comparison types require both revisions.");
                    unchanged++;
                }
                default -> throw new IOException("Unsupported status.");
            }
        }
        var expected = new CityDocument.Summary(baseCount, headCount, added, removed, modified, unchanged,
                document.diagnostics().stream().anyMatch(d -> d.severity().equals("error")));
        require(expected.equals(document.summary()), "Summary must match the exported verified types and diagnostics.");
    }

    private static boolean checkSide(CityDocument.Source source, CityDocument.Metrics metrics, String kind)
            throws IOException {
        if (source == null) {
            require(metrics == null && kind == null, "Absent source requires null metrics and kind.");
            return false;
        }
        require(metrics != null && kind != null, "Present source requires metrics and kind.");
        require(source.endLine() >= source.startLine()
                && metrics.sourceLines() == source.endLine() - source.startLine() + 1,
                "Source span and sourceLines must agree.");
        return true;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}
