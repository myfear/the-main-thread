package com.themainthread.workshop;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

@Path("/review")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReviewResource {

    private static final Logger LOG = Logger.getLogger(ReviewResource.class);

    private final CodeReviewer reviewer;

    public ReviewResource(CodeReviewer reviewer) {
        this.reviewer = reviewer;
    }

    @POST
    public Response review(ReviewRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()
                || request.question() == null || request.question().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Provide non-empty code and question strings.")).build();
        }
        if (request.code().length() > 8000 || request.question().length() > 1000) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Use a small example: code up to 8000 characters, question up to 1000."))
                    .build();
        }
        try {
            String answer = reviewer.review(request.code(), request.question());
            if (answer == null || answer.isBlank()) {
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(new ErrorResponse("The local model returned no answer. Try a shorter question."))
                        .build();
            }
            return Response.ok(new ReviewResponse(answer)).build();
        } catch (RuntimeException exception) {
            LOG.warn("Local model request failed", exception);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ErrorResponse("Local model request failed. Start Ollama, run the participant setup "
                            + "for workshop-qwen:1.7b, and check the configured local Ollama URL. See the application log."))
                    .build();
        }
    }

    public record ReviewRequest(String code, String question) {
    }

    public record ReviewResponse(String answer) {
    }

    public record ErrorResponse(String error) {
    }
}
