package com.themainthread.diagrams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DiagramCatalog {
    private final String source;

    public DiagramCatalog() {
        try (InputStream stream = getClass().getResourceAsStream("/diagrams/fulfillment.mmd")) {
            if (stream == null) {
                throw new IllegalStateException("Missing fulfillment diagram");
            }
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fulfillment diagram", exception);
        }
    }

    public String source() {
        return source;
    }
}
