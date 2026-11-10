package org.acme;

import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/runtime")
public class RuntimeResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> runtime() {
        return Map.of(
                "javaVersion", System.getProperty("java.version"),
                "javaRuntimeVersion", System.getProperty("java.runtime.version"),
                "javaVendor", System.getProperty("java.vendor"));
    }
}
