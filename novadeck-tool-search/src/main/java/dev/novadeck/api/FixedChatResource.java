package dev.novadeck.api;

import dev.novadeck.assistant.FixedOpsAssistant;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

@Path("/api/fixed")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FixedChatResource {

    private static final Logger LOG = Logger.getLogger(FixedChatResource.class);

    @Inject
    FixedOpsAssistant assistant;

    @POST
    @Path("/chat")
    public ChatResponseJson chat(ChatRequestBody body) {
        if (body == null || body.prompt == null || body.prompt.isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }
        long start = System.nanoTime();
        String reply = assistant.ask(body.prompt.strip());
        long ms = (System.nanoTime() - start) / 1_000_000L;
        LOG.debugf("fixed chat completed in %d ms", ms);
        return new ChatResponseJson("fixed", reply, ms);
    }

    public record ChatResponseJson(String mode, String reply, long elapsedMillis) {
    }
}
