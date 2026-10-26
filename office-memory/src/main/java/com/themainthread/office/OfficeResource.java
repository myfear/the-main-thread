package com.themainthread.office;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.Session;

import io.quarkus.narayana.jta.QuarkusTransaction;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OfficeResource {
    private final Session session;
    private final ConversationService conversations;
    private final MemoryService memories;

    public OfficeResource(Session session, ConversationService conversations, MemoryService memories) {
        this.session = session;
        this.conversations = conversations;
        this.memories = memories;
    }

    public record NewConversation(@NotNull UUID projectId, @NotBlank @Size(max = 120) String title) {
    }

    public record NewMessage(@NotBlank @Size(max = 4000) String text, @NotNull UUID requestId) {
    }

    public record Decision(@NotNull UUID projectId, @NotNull @Min(0) Long expectedVersion) {
    }

    @GET
    @Path("/projects")
    public List<Project> projects() {
        return QuarkusTransaction.requiringNew()
                .call(() -> session.createSelectionQuery("from Project order by name", Project.class).getResultList());
    }

    @POST
    @Path("/conversations")
    public Conversation create(@NotNull @Valid NewConversation body) {
        return conversations.create(body.projectId(), body.title());
    }

    @GET
    @Path("/conversations")
    public List<Conversation> conversations(@QueryParam("projectId") UUID projectId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            memories.requireProject(projectId);
            return session
                    .createSelectionQuery("from Conversation where projectId=:p order by updatedAt desc", Conversation.class)
                    .setParameter("p", projectId).getResultList();
        });
    }

    @GET
    @Path("/conversations/{id}/messages")
    public List<ConversationMessage> messages(@PathParam("id") UUID id) {
        return conversations.messages(id);
    }

    @POST
    @Path("/conversations/{id}/messages")
    public ApiViews.Turn send(@PathParam("id") UUID id, @NotNull @Valid NewMessage body) {
        return conversations.send(id, body.text(), body.requestId());
    }

    @GET
    @Path("/memories")
    public List<ApiViews.Fact> facts(@QueryParam("projectId") UUID projectId) {
        return memories.facts(projectId);
    }

    @GET
    @Path("/memories/{id}/history")
    public List<ApiViews.Fact> history(@PathParam("id") UUID id, @QueryParam("projectId") UUID projectId) {
        return memories.history(projectId, id);
    }

    @GET
    @Path("/proposals")
    public List<ApiViews.Proposal> proposals(@QueryParam("projectId") UUID projectId) {
        return memories.proposals(projectId);
    }

    @POST
    @Path("/proposals/{id}/approve")
    public ApiViews.Proposal approve(@PathParam("id") UUID id, @NotNull @Valid Decision body) {
        return memories.approve(body.projectId(), id, body.expectedVersion());
    }

    @POST
    @Path("/proposals/{id}/reject")
    public ApiViews.Proposal reject(@PathParam("id") UUID id, @NotNull @Valid Decision body) {
        return memories.reject(body.projectId(), id, body.expectedVersion());
    }

    @GET
    @Path("/tool-invocations")
    public List<ToolInvocation> tools(@QueryParam("conversationId") UUID conversationId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            memories.requireConversation(conversationId);
            return session
                    .createSelectionQuery("from ToolInvocation where conversationId=:c order by occurredAt",
                            ToolInvocation.class)
                    .setParameter("c", conversationId).getResultList();
        });
    }
}
