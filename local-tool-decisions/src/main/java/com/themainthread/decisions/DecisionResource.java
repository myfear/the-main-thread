package com.themainthread.decisions;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.smallrye.common.annotation.Blocking;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DecisionResource {
    private final ReviewService reviews;
    private final DemoTools tools;

    DecisionResource(ReviewService reviews, DemoTools tools) {
        this.reviews = reviews;
        this.tools = tools;
    }

    @POST
    @Path("reviews")
    public ReviewResponse review(ReviewRequest request) {
        return reviews.review(request);
    }

    @POST
    @Path("executions")
    public ReviewService.ExecutionResponse execute(ReviewRequest request) {
        return reviews.execute(request);
    }

    @GET
    @Path("demo/refunds")
    public Map<String, Long> refunds() {
        return tools.refunds();
    }
}
