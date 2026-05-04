package dev.novadeck.api;

import dev.novadeck.assistant.SearchAssistantClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

@Path("/api/search")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SearchChatResource {

    private static final Logger LOG = Logger.getLogger(SearchChatResource.class);

    @Inject
    SearchAssistantClient assistantClient;

    @POST
    @Path("/chat")
    public FixedChatResource.ChatResponseJson chat(ChatRequestBody body) {
        if (body == null || body.prompt == null || body.prompt.isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }
        long start = System.nanoTime();
        String reply = assistantClient.ask(body.prompt.strip());
        long ms = (System.nanoTime() - start) / 1_000_000L;
        LOG.debugf("search chat completed in %d ms", ms);
        return new FixedChatResource.ChatResponseJson("search", reply, ms);
    }
}
