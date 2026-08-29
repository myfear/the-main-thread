package com.themainthread.banner;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/thread")
@Produces(APPLICATION_JSON)
public class ThreadResource {

    @GET
    public ThreadStatus status() {
        return new ThreadStatus(
                "The Main Thread",
                "Because modern Java deserves better content.");
    }

    public record ThreadStatus(String name, String mission) {
    }
}
