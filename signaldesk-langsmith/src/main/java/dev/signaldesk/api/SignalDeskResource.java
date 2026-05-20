package dev.signaldesk.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.signaldesk.service.SignalDeskService;

@Path("/signaldesk")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SignalDeskResource {

    @Inject
    SignalDeskService signalDeskService;

    @POST
    @Path("/assist")
    public AssistResponse assist(AssistRequest body) {
        return signalDeskService.assist(body.question());
    }
}
