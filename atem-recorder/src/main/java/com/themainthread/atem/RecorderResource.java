package com.themainthread.atem;

import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;

@Path("/api/atem")
@Blocking
public class RecorderResource {
    private static final String DOWNLOAD_PATH = "/api/atem/recordings/download";
    private final MediaMtxClient media;

    RecorderResource(MediaMtxClient media) {
        this.media = media;
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Status status() {
        var status = media.status();
        return new Status(status.online(), status.connectedSince());
    }

    @GET
    @Path("recordings")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Recording> recordings() {
        return media.recordings().stream()
                .map(interval -> new Recording(interval.start(), interval.duration(),
                        DOWNLOAD_PATH + "?" + MediaMtxClient.rangeQuery(interval.start(), interval.duration())))
                .toList();
    }

    @GET
    @Path("recordings/download")
    @RunOnVirtualThread
    public Response download(@QueryParam("start") String start, @QueryParam("duration") String duration,
            @Context HttpHeaders headers, @Context ServerRequestContext request) {
        try {
            Instant from = parseStart(start);
            double seconds = parseDuration(duration);
            var upstream = media.download(from, seconds);
            InputStream body = upstream.body();
            request.registerCompletionCallback(failure -> MediaMtxClient.close(body));
            StreamingOutput output = destination -> {
                try (body) {
                    body.transferTo(destination);
                }
            };

            String stamp = DateTimeFormatter.ISO_INSTANT.format(from).replace(':', '-');
            var response = Response.ok(output, "video/mp4")
                    .header("Content-Disposition", "attachment; filename=\"atem-" + stamp + ".mp4\"")
                    .header("Cache-Control", "no-store");
            upstream.headers().firstValueAsLong("Content-Length")
                    .ifPresent(length -> response.header("Content-Length", length));
            return response.build();
        } catch (RecorderException e) {
            boolean html = headers.getAcceptableMediaTypes().stream()
                    .anyMatch(type -> type.getType().equals("text") && type.getSubtype().equals("html"));
            if (html) {
                return Response.status(e.status()).type(MediaType.TEXT_HTML)
                        .header("Cache-Control", "no-store")
                        .entity(DashboardPage.Templates.downloadError(e.getMessage()).render()).build();
            }
            return error(e);
        }
    }

    // Exporting a recording is expensive and the playback API has no HEAD operation.
    @HEAD
    @Path("recordings/download")
    public Response downloadHead() {
        return Response.status(405).allow("GET").build();
    }

    @ServerExceptionMapper
    public Response error(RecorderException e) {
        return Response.status(e.status()).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").entity(new Problem(e.getMessage())).build();
    }

    private static Instant parseStart(String value) {
        if (value != null) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                // Report a stable error message for the API.
            }
        }
        throw new RecorderException(400, "Start must be an RFC 3339 timestamp.");
    }

    private static double parseDuration(String value) {
        if (value != null) {
            try {
                double seconds = Double.parseDouble(value);
                if (Double.isFinite(seconds) && seconds > 0) {
                    return seconds;
                }
            } catch (NumberFormatException ignored) {
                // Report a stable error message for the API.
            }
        }
        throw new RecorderException(400, "Duration must be a positive finite number of seconds.");
    }

    public record Status(boolean online, Instant connectedSince) {
    }

    public record Recording(Instant start, double duration, String downloadUrl) {
    }

    public record Problem(String message) {
    }
}
