package com.mainthread.loanflow.loan;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@RequestScoped
public class CallerContext {

    private final SecurityIdentity identity;
    private final Instance<JsonWebToken> token;

    @Inject
    public CallerContext(SecurityIdentity identity, Instance<JsonWebToken> token) {
        this.identity = identity;
        this.token = token;
    }

    public boolean hasRole(String role) {
        return identity.hasRole(role);
    }

    public String branch() {
        if (token.isResolvable()) {
            String claim = token.get().getClaim("branch");
            if (claim != null) {
                return claim;
            }
        }
        return identity.getAttribute("branch");
    }

    public String principalName() {
        if (token.isResolvable()) {
            JsonWebToken jwt = token.get();
            String preferredUsername = jwt.getClaim("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            return jwt.getName();
        }
        String name = identity.getPrincipal().getName();
        return name != null ? name : "unknown";
    }
}
