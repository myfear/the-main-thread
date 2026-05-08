package dev.mainthread;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus " + System.getProperty("java.version");
    }

    @GET
    @Path("/work")
    @Produces(MediaType.TEXT_PLAIN)
    public String doWork() {
        List<String> values = new ArrayList<>(500_000);
        long result = 0;

        for (int i = 0; i < 500_000; i++) {
            String value = "value-" + i;
            values.add(value);
            result += value.length();
        }

        return "Computed: " + result + ", allocated strings: " + values.size();
    }
}