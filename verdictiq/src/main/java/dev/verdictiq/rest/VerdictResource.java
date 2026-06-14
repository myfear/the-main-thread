package dev.verdictiq.rest;

import dev.verdictiq.model.SubmissionAccepted;
import dev.verdictiq.model.SubmitVerdictRequest;
import dev.verdictiq.model.VerdictStatus;
import dev.verdictiq.service.VerdictPanel;
import dev.verdictiq.service.VerdictStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/verdict")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VerdictResource {

    private final VerdictPanel verdictPanel;
    private final VerdictStore store;

    public VerdictResource(VerdictPanel verdictPanel, VerdictStore store) {
        this.verdictPanel = verdictPanel;
        this.store = store;
    }

    @POST
    public Response submit(SubmitVerdictRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorMessage("text is required"))
                    .build();
        }

        String id = verdictPanel.submit(request.text());
        return Response.accepted(new SubmissionAccepted(id, VerdictStatus.PENDING.name())).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return store.find(id)
                .<Response>map(verdict -> Response.ok(verdict).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    public record ErrorMessage(String error) {
    }
}
