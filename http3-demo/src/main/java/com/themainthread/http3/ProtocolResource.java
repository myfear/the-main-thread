package com.themainthread.http3;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/protocol")
public class ProtocolResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String protocol(HttpServerRequest request) {
        return request.version().name();
    }
}
