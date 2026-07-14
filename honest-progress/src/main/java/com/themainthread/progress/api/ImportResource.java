package com.themainthread.progress.api;

import java.net.URI;
import java.util.UUID;

import com.themainthread.progress.domain.ImportProgress;
import com.themainthread.progress.job.ProgressStreamService;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    private final ImportJobService jobs;
    private final ProgressStreamService streams;
    private final Sse sse;

    public ImportResource(ImportJobService jobs, ProgressStreamService streams, Sse sse) {
        this.jobs = jobs;
        this.streams = streams;
        this.sse = sse;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response create(@NotNull @RestForm("file") FileUpload file, @Context UriInfo uriInfo) {
        ImportProgress progress = jobs.create(file);
        URI location = uriInfo.getAbsolutePathBuilder().path(progress.id().toString()).build();
        return Response.accepted(progress).location(location).build();
    }

    @GET
    @Path("/{id}")
    public ImportProgress get(@PathParam("id") UUID id) {
        return jobs.snapshot(id);
    }

    @DELETE
    @Path("/{id}")
    public Response cancel(@PathParam("id") UUID id) {
        return Response.accepted(jobs.cancel(id)).build();
    }

    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    public Multi<OutboundSseEvent> events(@PathParam("id") UUID id) {
        ImportProgress initial = jobs.snapshot(id);
        return streams.stream(id, initial).map(this::event);
    }

    private OutboundSseEvent event(ImportProgress progress) {
        return sse.newEventBuilder()
                .id(Long.toString(progress.version()))
                .name("progress")
                .reconnectDelay(2000)
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(ImportProgress.class, progress)
                .build();
    }
}
