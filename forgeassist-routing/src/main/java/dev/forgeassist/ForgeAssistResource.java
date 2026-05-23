package dev.forgeassist;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/assist")
public class ForgeAssistResource {

    private final ModelRouter router;

    @Inject
    public ForgeAssistResource(ModelRouter router) {
        this.router = router;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String ask(String question) {
        return router.route(question);
    }
}