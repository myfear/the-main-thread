package com.themainthread.ledgerlock;

import java.util.List;
import java.util.Set;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.smallrye.jwt.build.Jwt;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/dev/token")
@Produces(MediaType.APPLICATION_JSON)
@UnlessBuildProfile("prod")
public class DevTokenResource {

    @GET
    @Path("/{subject}")
    public TokenResponse token(@PathParam("subject") String subject) {
        String token = Jwt.subject(subject)
                .upn(subject)
                .groups(Set.of("user"))
                .claim("amr", List.of("pwd"))
                .sign();
        return new TokenResponse(token);
    }

    public record TokenResponse(String token) {
    }
}
