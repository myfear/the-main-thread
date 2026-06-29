package dev.mainthread.incidents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.qdrant.runtime.QdrantClient;
import io.quarkiverse.qdrant.runtime.model.PointStruct;
import io.quarkiverse.qdrant.runtime.model.ScoredPoint;

@ApplicationScoped
class IncidentArchive {

    private static final String COLLECTION = "incidents";
    private static final int DEFAULT_LIMIT = 5;
    private static final float DEFAULT_MIN_SCORE = 0.68f;

    private final QdrantClient qdrant;
    private final IncidentVectorizer vectorizer;
    private volatile boolean collectionChecked;

    @Inject
    IncidentArchive(QdrantClient qdrant, IncidentVectorizer vectorizer) {
        this.qdrant = qdrant;
        this.vectorizer = vectorizer;
    }

    IndexedIncident index(IncidentInput incident) {
        ensureCollection();
        String id = incidentId(incident);
        String pointId = pointIdFor(incident);
        qdrant.upsert(COLLECTION)
                .point(new PointStruct(pointId, vectorizer.vectorForPoint(incident), payloadFor(id, incident)))
                .execute();
        return new IndexedIncident(id, pointId, COLLECTION, IncidentVectorizer.DIMENSIONS);
    }

    SeedResponse seed(List<IncidentInput> incidents) {
        ensureCollection();
        List<String> ids = new ArrayList<>(incidents.size());
        List<PointStruct> points = new ArrayList<>(incidents.size());

        for (IncidentInput incident : incidents) {
            String id = incidentId(incident);
            String pointId = pointIdFor(incident);
            ids.add(id);
            points.add(new PointStruct(pointId, vectorizer.vectorForPoint(incident), payloadFor(id, incident)));
        }

        qdrant.upsert(COLLECTION).points(points).execute();
        return new SeedResponse(points.size(), ids);
    }

    SearchResponse search(SimilarIncidentRequest request) {
        ensureCollection();
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        float minScore = request.minScore() == null ? DEFAULT_MIN_SCORE : request.minScore();

        List<ScoredPoint> points = qdrant.search(COLLECTION)
                .vector(vectorizer.vectorForSearch(request.incident()))
                .limit(limit)
                .scoreThreshold(minScore)
                .withPayload(true)
                .withVector(false)
                .filter(filterFor(request))
                .execute();

        List<IncidentMatch> matches = points.stream()
                .map(IncidentArchive::matchFrom)
                .toList();
        return new SearchResponse(matches.size(), matches);
    }

    private synchronized void ensureCollection() {
        if (collectionChecked) {
            return;
        }
        if (!qdrant.listCollections().contains(COLLECTION)) {
            qdrant.createCollection(COLLECTION)
                    .vectorSize(IncidentVectorizer.DIMENSIONS)
                    .distance("Cosine")
                    .execute();
        }
        collectionChecked = true;
    }

    private static String incidentId(IncidentInput incident) {
        if (incident.id() != null && !incident.id().isBlank()) {
            return incident.id();
        }
        return pointIdFor(incident);
    }

    private static String pointIdFor(IncidentInput incident) {
        String source = incident.service() + "|" + incident.environment() + "|" + incident.exceptionType() + "|"
                + incident.message() + "|" + incident.stackTrace();
        if (incident.id() != null && !incident.id().isBlank()) {
            source = incident.id() + "|" + source;
        }
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Map<String, Object> payloadFor(String id, IncidentInput incident) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("pointId", pointIdFor(incident));
        payload.put("service", incident.service());
        payload.put("environment", incident.environment());
        payload.put("exceptionType", incident.exceptionType());
        payload.put("message", incident.message());
        payload.put("stackTrace", incident.stackTrace());
        payload.put("resolved", incident.resolvedBy() != null && !incident.resolvedBy().isBlank());
        putIfPresent(payload, "resolvedBy", incident.resolvedBy());
        putIfPresent(payload, "incidentUrl", incident.incidentUrl());
        return payload;
    }

    private static Map<String, Object> filterFor(SimilarIncidentRequest request) {
        List<Map<String, Object>> must = new ArrayList<>();
        addMatch(must, "service", request.filterService());
        addMatch(must, "environment", request.filterEnvironment());
        if (Boolean.TRUE.equals(request.onlyResolved())) {
            addMatch(must, "resolved", true);
        }
        if (must.isEmpty()) {
            return null;
        }
        return Map.of("must", must);
    }

    private static void addMatch(List<Map<String, Object>> must, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        must.add(Map.of("key", key, "match", Map.of("value", value)));
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static IncidentMatch matchFrom(ScoredPoint point) {
        Map<String, Object> payload = point.getPayload();
        return new IncidentMatch(
                valueOrFallback(payload, "id", point.getId()),
                point.getScore(),
                stringValue(payload, "service"),
                stringValue(payload, "environment"),
                stringValue(payload, "exceptionType"),
                stringValue(payload, "message"),
                stringValue(payload, "resolvedBy"),
                stringValue(payload, "incidentUrl"));
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : value.toString();
    }

    private static String valueOrFallback(Map<String, Object> payload, String key, String fallback) {
        String value = stringValue(payload, key);
        return value == null ? fallback : value;
    }
}
