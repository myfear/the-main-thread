package com.themainthread.vex;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.packageurl.PackageURL;

final class SbomReader {
    private static final Pattern MAVEN = Pattern.compile("^pkg:maven/([^/@?#]+)/([^/@?#]+)@([^/?#]+)(?:\\?[^#]+)?$");

    record Coordinate(String group, String artifact, String version) {
        String name() { return group + ":" + artifact; }
    }

    record Component(String purl, String bomRef, String sourceModule, String scope, Coordinate gav) {
        String key() { return scope + "|" + sourceModule + "|" + bomRef + "|" + purl; }
    }

    record Inventory(String product, String sourceModule, List<Component> components) {}

    static void validateProduct(String product) {
        try {
            var parsed = new PackageURL(product);
            EvidenceHasher.require(parsed.getVersion() != null && !parsed.getVersion().isBlank(), "Product must have a version");
        } catch (Exception e) { throw new CaseException(2, "Invalid versioned product PURL"); }
    }

    private static String module(String purl) {
        var c = coordinate(purl);
        return "pkg:maven/" + c.group() + "/" + c.artifact() + "@" + c.version();
    }

    static Coordinate coordinate(String purl) {
        var matcher = MAVEN.matcher(purl);
        if (!matcher.matches()) throw new CaseException(2, "Expected a versioned Maven PURL: " + purl);
        return new Coordinate(decode(matcher.group(1)), decode(matcher.group(2)), decode(matcher.group(3)));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    Inventory read(Path file, String productOverride, boolean dependency) throws IOException {
        JsonNode root = EvidenceHasher.read(file);
        EvidenceHasher.require(root.path("bomFormat").asText().equals("CycloneDX"), "Not CycloneDX");
        String declared = root.path("metadata").path("component").path("purl").asText();
        String product = productOverride == null ? declared : productOverride;
        validateProduct(product);
        EvidenceHasher.require(declared.isBlank() || declared.equals(product), "Product override disagrees with SBOM");
        EvidenceHasher.require(root.path("components").isArray(), "Missing SBOM components");
        String sourceModule = product.startsWith("pkg:maven/") ? module(product) : "";
        for (JsonNode entry : root.path("components")) {
            for (JsonNode occurrence : entry.path("evidence").path("occurrences")) {
                if (occurrence.path("location").asText().startsWith("app/") && entry.path("purl").asText().startsWith("pkg:maven/")) {
                    EvidenceHasher.require(sourceModule.isBlank() || sourceModule.equals(module(entry.path("purl").asText())), "Ambiguous source module");
                    sourceModule = module(entry.path("purl").asText());
                }
            }
        }
        EvidenceHasher.require(!sourceModule.isBlank(), "Cannot establish the source application module");
        List<Component> components = new ArrayList<>();
        HashSet<String> refs = new HashSet<>();
        for (JsonNode entry : root.path("components")) {
            if (!entry.path("type").asText().equals("library") || !entry.path("purl").asText().startsWith("pkg:maven/"))
                continue;
            String purl = entry.path("purl").asText();
            Coordinate gav = coordinate(purl);
            EvidenceHasher.require(entry.path("version").asText().equals(gav.version()), "PURL/version mismatch");
            String ref = entry.path("bom-ref").asText();
            EvidenceHasher.require(!ref.isBlank() && refs.add(ref), "Missing or ambiguous bom-ref");
            String quarkusScope = "";
            for (JsonNode property : entry.path("properties")) {
                if (property.path("name").asText().equals("quarkus:component:scope"))
                    quarkusScope = property.path("value").asText();
            }
            boolean build = entry.path("scope").asText().equals("excluded") && quarkusScope.equals("development");
            if (dependency) {
                if (!build) continue;
            } else {
                EvidenceHasher.require(!entry.path("scope").asText().equals("excluded")
                        && quarkusScope.equals("runtime"), "Distribution must contain runtime-only scoped libraries");
            }
            components.add(new Component(purl, ref, sourceModule, dependency ? "build" : "runtime", gav));
        }
        return new Inventory(product, sourceModule, List.copyOf(components));
    }
}
