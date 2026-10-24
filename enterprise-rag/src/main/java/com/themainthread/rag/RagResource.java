package com.themainthread.rag;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import io.smallrye.common.annotation.Blocking;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class RagResource {
    private static final Logger LOG = Logger.getLogger(RagResource.class);
    private final IndexingState state;
    private final DocumentLoader loader;
    private final DocumentRetriever retriever;
    private final SalesEnablementBot bot;

    public RagResource(IndexingState state, DocumentLoader loader, DocumentRetriever retriever, SalesEnablementBot bot) {
        this.state = state;
        this.loader = loader;
        this.retriever = retriever;
        this.bot = bot;
    }

    public record Source(String file, int segment, String text) {
        static Source from(TextSegment segment) {
            return new Source(segment.metadata().getString("file_name"), segment.metadata().getInteger("index"),
                    segment.text());
        }
    }

    public record BotResponse(String status, String response, List<Source> sources) {
    }

    @GET
    @Path("ingestion")
    public IndexingState.Snapshot ingestion() {
        return state.snapshot();
    }

    @GET
    @Path("chunks")
    public Response chunks() {
        if (!state.ready()) {
            return unavailable();
        }
        return Response.ok(loader.chunks().stream().map(Source::from).toList()).build();
    }

    @GET
    @Path("retrieval")
    public Response retrieval(@QueryParam("q") String question) {
        if (!state.ready()) {
            return unavailable();
        }
        if (!valid(question)) {
            return invalid();
        }
        return Response.ok(sources(retriever.retrieve(question.strip()))).build();
    }

    @GET
    @Path("bot")
    public Response ask(@QueryParam("q") String question) {
        if (!state.ready()) {
            return unavailable();
        }
        if (!valid(question)) {
            return invalid();
        }
        try {
            var result = bot.answer(question.strip());
            var evidence = sources(result.sources());
            if ("NOT_FOUND".equals(result.content().strip())) {
                return Response.ok(new BotResponse("not_found", "The retrieved documents do not answer this question.",
                        evidence)).build();
            }
            return Response.ok(new BotResponse("answered", result.content(), evidence)).build();
        } catch (Exception failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof DocumentRetriever.NoEvidenceException) {
                    return Response.ok(new BotResponse("not_found", "No document excerpts passed the retrieval threshold.",
                            List.of())).build();
                }
            }
            LOG.error("Question processing failed", failure);
            return Response.status(502).entity(new BotResponse("error", "A retrieval or generation service failed.",
                    List.of())).build();
        }
    }

    private static List<Source> sources(List<Content> contents) {
        return contents.stream().map(Content::textSegment).map(Source::from).distinct().toList();
    }

    private static boolean valid(String question) {
        return question != null && !question.isBlank() && question.length() <= 1000;
    }

    private static Response invalid() {
        return Response.status(400).entity(new BotResponse("invalid", "Supply q with 1 to 1000 characters.", List.of()))
                .build();
    }

    private Response unavailable() {
        return Response.status(503).entity(new BotResponse("unavailable", state.snapshot().detail(), List.of())).build();
    }
}
