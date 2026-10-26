package com.themainthread.studio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@StudioAccess
public class StudioAccessFilter implements ContainerRequestFilter {
    private final Instance<StudioConfig> config;

    public StudioAccessFilter(Instance<StudioConfig> config) {
        this.config = config;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!matches("Bearer " + config.get().runnerToken(), request.getHeaderString("Authorization"))) {
            request.abortWith(Response.status(401).build());
        }
    }

    public static boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
