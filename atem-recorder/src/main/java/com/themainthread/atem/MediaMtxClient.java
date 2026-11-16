package com.themainthread.atem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

@ApplicationScoped
class MediaMtxClient {
    private static final Logger LOG = Logger.getLogger(MediaMtxClient.class);

    private final RecorderConfig config;
    private final HttpClient client;
    private final ObjectReader json;

    MediaMtxClient(RecorderConfig config, ObjectMapper mapper) {
        this.config = config;
        client = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        json = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PreDestroy
    void stop() {
        client.shutdownNow();
    }

    StreamStatus status() {
        URI uri = UriBuilder.fromUri(config.controlUrl())
                .path("v3/paths/get").path(config.streamPath()).build();
        var response = send(uri, config.metadataTimeout(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return new StreamStatus(false, null);
        }
        requireSuccess(response.statusCode(), "Stream status");
        JsonNode path = readJson(response.body());
        if (!path.path("online").isBoolean()) {
            throw invalidResponse();
        }
        boolean online = path.path("online").booleanValue();
        return new StreamStatus(online, online ? timestamp(path.path("onlineTime")) : null);
    }

    List<Interval> recordings() {
        var response = send(playbackUri("list", ""), config.metadataTimeout(), HttpResponse.BodyHandlers.ofString());
        // MediaMTX returns 404 when there are no recorded segments for the path.
        if (response.statusCode() == 404) {
            return List.of();
        }
        requireSuccess(response.statusCode(), "Recording library");
        JsonNode ranges = readJson(response.body());
        if (!ranges.isArray()) {
            throw invalidResponse();
        }

        var intervals = new ArrayList<Interval>();
        for (JsonNode range : ranges) {
            JsonNode duration = range.path("duration");
            double seconds = duration.doubleValue();
            if (!duration.isNumber() || !Double.isFinite(seconds) || seconds <= 0) {
                throw invalidResponse();
            }
            intervals.add(new Interval(timestamp(range.path("start")), seconds));
        }
        intervals.sort(Comparator.comparing(Interval::start).reversed());
        return intervals;
    }

    HttpResponse<InputStream> download(Instant start, double duration) {
        URI uri = playbackUri("get", "&" + rangeQuery(start, duration) + "&format=mp4");
        var response = send(uri, config.downloadHeaderTimeout(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200
                && response.headers().firstValue("Content-Type").orElse("").equalsIgnoreCase("video/mp4")) {
            return response;
        }

        close(response.body());
        if (response.statusCode() == 404) {
            throw new RecorderException(404, "This recording range is no longer available.");
        }
        requireSuccess(response.statusCode(), "MP4 export");
        throw invalidResponse();
    }

    static String rangeQuery(Instant start, double duration) {
        return "start=" + encode(start.toString()) + "&duration=" + duration;
    }

    static void close(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            LOG.debug("Could not close MediaMTX response", e);
        }
    }

    private URI playbackUri(String endpoint, String query) {
        URI base = UriBuilder.fromUri(config.playbackUrl()).path(endpoint).build();
        return URI.create(base + "?path=" + encode(config.streamPath()) + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <T> HttpResponse<T> send(URI uri, Duration timeout, HttpResponse.BodyHandler<T> handler) {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        try {
            return client.send(request, handler);
        } catch (HttpTimeoutException e) {
            throw new RecorderException(504, "MediaMTX did not respond in time.");
        } catch (IOException e) {
            LOG.debugf(e, "MediaMTX request failed for %s", uri.getPath());
            throw new RecorderException(503, "MediaMTX is unreachable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecorderException(503, "The recorder request was interrupted.");
        }
    }

    private JsonNode readJson(String body) {
        try {
            return json.readValue(body);
        } catch (IOException e) {
            throw invalidResponse();
        }
    }

    private static Instant timestamp(JsonNode field) {
        if (field.isTextual()) {
            try {
                return Instant.parse(field.textValue());
            } catch (DateTimeParseException ignored) {
                // Reject malformed upstream timestamps.
            }
        }
        throw invalidResponse();
    }

    private static void requireSuccess(int status, String operation) {
        if (status != 200) {
            LOG.debugf("%s returned HTTP %d from MediaMTX", operation, status);
            throw new RecorderException(502, operation + " failed at MediaMTX (HTTP " + status + ").");
        }
    }

    private static RecorderException invalidResponse() {
        return new RecorderException(502, "MediaMTX returned an unexpected response.");
    }

    record StreamStatus(boolean online, Instant connectedSince) {
    }

    record Interval(Instant start, double duration) {
    }
}
