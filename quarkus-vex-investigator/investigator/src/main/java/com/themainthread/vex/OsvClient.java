package com.themainthread.vex;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.maven.artifact.versioning.ComparableVersion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** No target code is executed. Every response and failed attempt becomes evidence. */
final class OsvClient {
    private static final int MAX_PAGES = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final URI base;
    private final Duration timeout;
    private final HttpClient client;
    private final Path root;
    private final Map<String, JsonNode> details = new HashMap<>();
    private int sequence;

    OsvClient(String baseUrl, Path root, Duration timeout) {
        base = URI.create(baseUrl);
        checkBaseUrl(base);
        this.root = root;
        this.timeout = timeout;
        client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private void checkBaseUrl(URI uri) {
        boolean local = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if ((!uri.getScheme().equals("https") && !(uri.getScheme().equals("http") && local))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new CaseException(2, "OSV requires HTTPS, or loopback HTTP for tests");
        }
    }

    ObjectNode query(SbomReader.Component component) throws IOException {
        ObjectNode result = newLookupOutcome(component);
        var findings = result.putArray("findings");
        try {
            queryAllPages(component, findings);
            result.put("complete", true);
        } catch (Exception failure) {
            result.put("complete", false);
            result.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return result;
    }

    private ObjectNode newLookupOutcome(SbomReader.Component component) {
        var result = EvidenceHasher.object();
        result.put("component_key", component.key());
        result.put("component_purl", component.purl());
        result.put("component_ref", component.bomRef());
        result.put("source_module", component.sourceModule());
        result.put("scope", component.scope());
        result.put("queried_at", Instant.now().toString());
        return result;
    }

    private void queryAllPages(SbomReader.Component component, ArrayNode findings) throws Exception {
        Set<String> tokens = new HashSet<>();
        Set<String> ids = new HashSet<>();
        String next = "";
        int pages = 0;
        do {
            EvidenceHasher.require(++pages <= MAX_PAGES, "OSV page limit");
            JsonNode response = exchange("/v1/query", queryRequest(component, next));
            checkQueryResponse(response);
            appendFindings(component, response, ids, findings);
            next = checkedNextPageToken(response, tokens);
        } while (!next.isBlank());
    }

    private ObjectNode queryRequest(SbomReader.Component component, String token) {
        var request = EvidenceHasher.object();
        request.putObject("package").put("ecosystem", "Maven").put("name", component.gav().name());
        request.put("version", component.gav().version());
        if (!token.isBlank()) {
            request.put("page_token", token);
        }
        return request;
    }

    private void checkQueryResponse(JsonNode response) {
        EvidenceHasher.require(!response.has("results") && !response.has("error"), "Unexpected OSV response shape");
        for (var field : response.properties()) {
            EvidenceHasher.require(List.of("vulns", "next_page_token").contains(field.getKey()),
                    "Unknown OSV query response field");
        }
        EvidenceHasher.require(!response.has("vulns") || response.get("vulns").isArray(), "Invalid OSV vulns");
    }

    private String checkedNextPageToken(JsonNode response, Set<String> tokens) {
        EvidenceHasher.require(!response.has("next_page_token") || response.get("next_page_token").isTextual(),
                "Invalid pagination token");
        String next = response.path("next_page_token").asText();
        EvidenceHasher.require(next.isBlank() || tokens.add(next), "Repeated OSV page token");
        return next;
    }

    private void appendFindings(SbomReader.Component component, JsonNode response, Set<String> ids,
            ArrayNode findings) throws Exception {
        for (JsonNode summary : response.path("vulns")) {
            String id = summary.path("id").asText();
            EvidenceHasher.require(id.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,160}"), "Invalid advisory ID");
            if (!ids.add(id)) {
                continue;
            }
            JsonNode advisory = fetchAdvisory(id);
            EvidenceHasher.require(!advisory.has("withdrawn") && affects(advisory, component.gav()),
                    "Advisory package/version could not be independently matched");
            findings.add(saveAdvisoryFinding(id, advisory));
        }
    }

    private JsonNode fetchAdvisory(String id) throws Exception {
        JsonNode advisory = details.get(id);
        if (advisory == null) {
            advisory = exchange("/v1/vulns/" + id, null);
            EvidenceHasher.require(advisory.path("id").asText().equals(id), "Enrichment identity mismatch");
            details.put(id, advisory);
        }
        return advisory;
    }

    private ObjectNode saveAdvisoryFinding(String id, JsonNode advisory) throws IOException {
        String relative = "evidence/advisory-" + id + ".json";
        EvidenceHasher.write(EvidenceHasher.safe(root, relative), advisory);
        var finding = EvidenceHasher.object();
        finding.put("id", id);
        finding.set("aliases", advisory.path("aliases").isArray() ? advisory.get("aliases") : EvidenceHasher.JSON.createArrayNode());
        finding.put("advisory_path", relative);
        finding.put("advisory_sha256", EvidenceHasher.hash(root.resolve(relative)));
        finding.put("modified", advisory.path("modified").asText());
        return finding;
    }

    private JsonNode exchange(String endpoint, JsonNode body) throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ObjectNode metadata = newAttemptMetadata(endpoint, body, attempt);
            String prefix = "evidence/osv-" + (++sequence);
            try {
                return requestAndCaptureResponse(endpoint, body, prefix, metadata);
            } catch (Exception failure) {
                metadata.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
                if (!isRetryable(failure) || attempt == MAX_ATTEMPTS) {
                    throw failure;
                }
            } finally {
                metadata.put("finished_at", Instant.now().toString());
                EvidenceHasher.write(EvidenceHasher.safe(root, prefix + "-metadata.json"), metadata);
            }
            Thread.sleep(100L * attempt);
        }
        throw new IOException("OSV retries exhausted");
    }

    private ObjectNode newAttemptMetadata(String endpoint, JsonNode body, int attempt) {
        var metadata = EvidenceHasher.object();
        metadata.put("endpoint", endpoint).put("attempt", attempt).put("started_at", Instant.now().toString());
        if (body != null) {
            metadata.set("request", body);
        }
        return metadata;
    }

    private JsonNode requestAndCaptureResponse(String endpoint, JsonNode body, String prefix, ObjectNode metadata)
            throws Exception {
        HttpRequest request = httpRequest(endpoint, body);
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        metadata.put("http_status", response.statusCode());
        byte[] bytes = readBoundedBody(response.body());
        captureResponse(prefix, metadata, bytes);
        checkHttpStatus(response.statusCode());
        JsonNode parsed = EvidenceHasher.JSON.readTree(bytes);
        EvidenceHasher.require(parsed != null && parsed.isObject(), "Invalid OSV object");
        return parsed;
    }

    private HttpRequest httpRequest(String endpoint, JsonNode body) {
        var builder = HttpRequest.newBuilder(base.resolve(endpoint)).timeout(timeout).header("Accept", "application/json");
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        return builder.build();
    }

    private byte[] readBoundedBody(InputStream body) throws Exception {
        // Header timeout alone does not bound a stalled body. Close it on the same deadline.
        try (InputStream input = body; var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> input.readNBytes(EvidenceHasher.MAX_JSON_BYTES + 1));
            byte[] bytes;
            try {
                bytes = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception failure) {
                input.close();
                future.cancel(true);
                throw failure;
            }
            EvidenceHasher.require(bytes.length <= EvidenceHasher.MAX_JSON_BYTES, "OSV response size limit");
            return bytes;
        }
    }

    private void captureResponse(String prefix, ObjectNode metadata, byte[] bytes) throws IOException {
        Path raw = EvidenceHasher.safe(root, prefix + "-response.json");
        FilesSupport.writeBytes(raw, bytes);
        metadata.put("response_path", prefix + "-response.json").put("sha256", EvidenceHasher.hash(raw));
    }

    private void checkHttpStatus(int status) throws IOException {
        if (status == 429 || status >= 500) {
            throw new IOException("Transient OSV HTTP " + status);
        }
        if (status != 200) {
            throw new CaseException(3, "OSV HTTP " + status);
        }
    }

    private boolean isRetryable(Exception failure) {
        return (failure instanceof IOException && !(failure instanceof JsonProcessingException))
                || failure instanceof TimeoutException;
    }

    static boolean affects(JsonNode advisory, SbomReader.Coordinate gav) {
        for (JsonNode affected : advisory.path("affected")) {
            if (!matchesMavenPackage(affected.path("package"), gav)) {
                continue;
            }
            if (includesExplicitVersion(affected, gav.version())) {
                return true;
            }
            for (JsonNode range : affected.path("ranges")) {
                if (range.path("type").asText().equals("ECOSYSTEM") && includesVersionRange(range, gav.version())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesMavenPackage(JsonNode pkg, SbomReader.Coordinate gav) {
        return pkg.path("ecosystem").asText().equals("Maven") && pkg.path("name").asText().equals(gav.name());
    }

    private static boolean includesExplicitVersion(JsonNode affected, String candidate) {
        for (JsonNode version : affected.path("versions")) {
            if (version.asText().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean includesVersionRange(JsonNode range, String version) {
        boolean active = false;
        for (JsonNode event : range.path("events")) {
            if (event.has("introduced")) {
                active = event.get("introduced").asText().equals("0")
                        || compare(version, event.get("introduced").asText()) >= 0;
            }
            if (event.has("fixed")) {
                if (active && compare(version, event.get("fixed").asText()) < 0) {
                    return true;
                }
                active = false;
            }
            if (event.has("last_affected")) {
                if (active && compare(version, event.get("last_affected").asText()) <= 0) {
                    return true;
                }
                active = false;
            }
            if (event.has("limit")) {
                if (active && compare(version, event.get("limit").asText()) < 0) {
                    return true;
                }
                active = false;
            }
        }
        return active;
    }

    private static int compare(String left, String right) {
        return new ComparableVersion(left).compareTo(new ComparableVersion(right));
    }
}
