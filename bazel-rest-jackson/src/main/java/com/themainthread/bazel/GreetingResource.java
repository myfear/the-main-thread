package com.themainthread.bazel;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/hello")
public class GreetingResource {

    @GET
    public Greeting hello() {
        return new Greeting("Hello from Bazel");
    }

    public record Greeting(String message) {
    }
}
