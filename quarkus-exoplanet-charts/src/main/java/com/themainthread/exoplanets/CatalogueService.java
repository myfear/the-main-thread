package com.themainthread.exoplanets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.cache.CacheResult;

@ApplicationScoped
public class CatalogueService {
    public enum Source { snapshot, live }

    private final ArchiveClient client;
    private final ObjectMapper mapper;

    public CatalogueService(@RestClient ArchiveClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @CacheResult(cacheName = "catalogue")
    public Catalogue load(Source source) {
        try {
            Catalogue catalogue;
            if (source == Source.snapshot) {
                try (InputStream input = resource("snapshot.json")) {
                    catalogue = mapper.readValue(input, Catalogue.class);
                }
            } else {
                String query;
                try (InputStream input = resource("query.sql")) {
                    query = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
                }
                var planets = client.query(query, "json");
                catalogue = new Catalogue(Instant.now().toString(), query, planets);
            }
            validate(catalogue);
            return catalogue;
        } catch (IOException | RuntimeException exception) {
            throw new CatalogueUnavailableException("Could not load the " + source + " catalogue", exception);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = CatalogueService.class.getResourceAsStream("/data/" + name);
        if (input == null) {
            throw new IOException("Missing data/" + name);
        }
        return input;
    }

    private static void validate(Catalogue catalogue) {
        if (catalogue.planets().isEmpty()) {
            throw new IllegalArgumentException("Archive returned an empty catalogue");
        }
        var names = new HashSet<String>();
        for (Planet planet : catalogue.planets()) {
            if (planet.name() == null || planet.name().isBlank() || !names.add(planet.name())) {
                throw new IllegalArgumentException("Expected one named row per planet");
            }
        }
    }

    public static class CatalogueUnavailableException extends RuntimeException {
        public CatalogueUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
