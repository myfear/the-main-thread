package com.themainthread.fernbank;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SkillCatalog {

    private static final List<String> MANIFEST_PATHS = List.of(
            "/skills/docs_generate.json",
            "/skills/pptx_export.json",
            "/skills/unsigned_status.json");

    private final ObjectMapper objectMapper;
    private Map<String, SkillManifest> manifests;

    SkillCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        manifests = MANIFEST_PATHS.stream()
                .map(this::readManifest)
                .collect(Collectors.toUnmodifiableMap(SkillManifest::skillId, Function.identity()));
    }

    public Optional<SkillManifest> find(String skillId) {
        return Optional.ofNullable(manifests.get(skillId));
    }

    private SkillManifest readManifest(String path) {
        try (InputStream stream = SkillCatalog.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Skill manifest not found: " + path);
            }
            return objectMapper.readValue(stream, SkillManifest.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read skill manifest: " + path, e);
        }
    }
}
