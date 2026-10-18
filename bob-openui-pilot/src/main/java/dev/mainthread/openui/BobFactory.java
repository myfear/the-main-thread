package dev.mainthread.openui;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
class BobFactory {
    private final BobConfig config;
    private final ObjectMapper mapper;

    BobFactory(BobConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    BobConnection open() throws Exception {
        String key = config.apiKey().orElse("");
        if (key.isBlank() && config.keyFile().isPresent()) {
            key = mapper.readTree(Files.readString(Path.of(config.keyFile().get()))).path("apikey").asText();
        }
        if (key.isBlank()) {
            throw new IllegalStateException("Set BOB_KEY_FILE to a credential JSON containing apikey, or set BOB_API_KEY.");
        }
        return new StdioBobConnection(config, key);
    }
}
