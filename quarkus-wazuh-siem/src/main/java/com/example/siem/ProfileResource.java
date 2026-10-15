package com.example.siem;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@Path("/api/profile")
public class ProfileResource {
    private final SecurityIdentity identity;

    public ProfileResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    @GET
    @RolesAllowed("user")
    public Profile profile() {
        return new Profile(identity.getPrincipal().getName(), "authenticated");
    }

    public record Profile(String principal, String status) {
    }
}
