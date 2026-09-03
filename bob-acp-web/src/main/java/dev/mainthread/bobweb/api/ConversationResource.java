package dev.mainthread.bobweb.api;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import dev.mainthread.bobweb.api.ApiModels.ActionAccepted;
import dev.mainthread.bobweb.api.ApiModels.ChangeModeRequest;
import dev.mainthread.bobweb.api.ApiModels.ConversationSummary;
import dev.mainthread.bobweb.api.ApiModels.ConversationView;
import dev.mainthread.bobweb.api.ApiModels.CreateConversationRequest;
import dev.mainthread.bobweb.api.ApiModels.PermissionDecisionRequest;
import dev.mainthread.bobweb.api.ApiModels.SendMessageRequest;
import dev.mainthread.bobweb.api.ApiModels.UiEvent;
import dev.mainthread.bobweb.session.ConversationService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("/api/conversations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationResource {

    private final ConversationService conversations;

    public ConversationResource(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GET
    public List<ConversationSummary> list() {
        return conversations.list();
    }

    @POST
    public Uni<Response> create(CreateConversationRequest request) {
        String workspace = request == null ? "." : request.workspace();
        return Uni.createFrom().completionStage(conversations.create(workspace))
                .map(view -> Response.status(Response.Status.CREATED).entity(view).build());
    }

    @GET
    @Path("/{id}")
    public ConversationView get(@PathParam("id") String id) {
        return conversations.get(id);
    }

    @POST
    @Path("/{id}/messages")
    public Response sendMessage(@PathParam("id") String id, SendMessageRequest request) {
        String prompt = request == null ? null : request.prompt();
        return Response.accepted(conversations.sendMessage(id, prompt)).build();
    }

    @PUT
    @Path("/{id}/mode")
    public Uni<ActionAccepted> changeMode(@PathParam("id") String id, ChangeModeRequest request) {
        String mode = request == null ? null : request.modeId();
        return Uni.createFrom().completionStage(conversations.changeMode(id, mode));
    }

    @POST
    @Path("/{id}/permissions/{toolCallId}")
    public ActionAccepted decidePermission(@PathParam("id") String id, @PathParam("toolCallId") String toolCallId,
            PermissionDecisionRequest request) {
        String option = request == null ? null : request.optionId();
        return conversations.decidePermission(id, toolCallId, option);
    }

    @DELETE
    @Path("/{id}/turn")
    public ActionAccepted cancel(@PathParam("id") String id) {
        return conversations.cancel(id);
    }

    @DELETE
    @Path("/{id}")
    public ActionAccepted close(@PathParam("id") String id) {
        return conversations.close(id);
    }

    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> events(@PathParam("id") String id,
            @QueryParam("after") @DefaultValue("0") long afterSequence, @Context Sse sse) {
        return conversations.events(id, afterSequence).map(event -> toSseEvent(sse, event));
    }

    private static OutboundSseEvent toSseEvent(Sse sse, UiEvent event) {
        return sse.newEventBuilder()
                .id(Long.toString(event.sequence()))
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(UiEvent.class, event)
                .build();
    }
}
