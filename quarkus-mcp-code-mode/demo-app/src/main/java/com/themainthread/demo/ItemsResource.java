package com.themainthread.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/items")
@ApplicationScoped
public class ItemsResource {
    private static final Logger LOG = Logger.getLogger(ItemsResource.class);
    private boolean emitted;

    @GET
    public synchronized List<String> items() throws IOException {
        if (!emitted) {
            try (var stream = ItemsResource.class.getResourceAsStream("/events.jsonl")) {
                if (stream == null) throw new IOException("Missing demo events");
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    for (String line; (line = reader.readLine()) != null;) {
                        LOG.info("R " + line);
                    }
                }
            }
            LOG.error("DEMO_ERROR payment retry exhausted for request pay-1");
            emitted = true;
        }
        return List.of("pencil", "notebook");
    }
}
