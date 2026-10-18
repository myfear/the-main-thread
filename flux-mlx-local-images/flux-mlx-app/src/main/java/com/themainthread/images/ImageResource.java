package com.themainthread.images;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import dev.langchain4j.model.output.FinishReason;
import io.smallrye.common.annotation.Blocking;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ImageResource {
    private final CaptionWriter writer;
    private final ImageJobs jobs;

    public ImageResource(CaptionWriter writer, ImageJobs jobs) {
        this.writer = writer;
        this.jobs = jobs;
    }

    @POST
    @Path("/captions")
    @Blocking
    public Caption expand(@NotNull @Valid Brief request) {
        String caption;
        try {
            var result = writer.expand(request.brief());
            if (result.finishReason() != FinishReason.STOP) {
                throw new IllegalStateException("The caption model did not finish normally");
            }
            caption = result.content();
        } catch (RuntimeException e) {
            throw new WebApplicationException("The local caption model is unavailable", 502);
        }
        if (caption == null || caption.isBlank() || caption.length() > 4000) {
            throw new WebApplicationException("The caption model returned an invalid caption", 502);
        }
        return new Caption(request.brief(), caption.strip());
    }

    @POST
    @Path("/images")
    public Response generate(@NotNull @Valid GenerateRequest request) {
        JobView job = jobs.submit(request);
        return Response.accepted(job).location(URI.create("/api/jobs/" + job.id())).build();
    }

    @GET
    @Path("/jobs/{id}")
    public JobView status(@PathParam("id") UUID id) {
        return jobs.get(id);
    }

    @GET
    @Path("/jobs/{id}/image")
    @Produces("image/png")
    public Response image(@PathParam("id") UUID id) {
        return Response.ok(jobs.image(id).toFile()).header("Cache-Control", "no-store").build();
    }

    public record Brief(@NotBlank @Size(max = 2000) String brief) {
    }

    public record Caption(String brief, String caption) {
    }
}
