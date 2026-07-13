package com.themainthread.ledgerlock;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StepUpTokenService {

    static final long LIFESPAN_SECONDS = 120;

    public String issueFor(String subject) {
        return Jwt.subject(subject)
                .upn(subject)
                .groups(Set.of("user", "payout:approve"))
                .claim("amr", List.of("pwd", "otp"))
                .claim("acr", "urn:ledgerlock:assurance:mfa")
                .expiresAt(Instant.now().plusSeconds(LIFESPAN_SECONDS))
                .sign();
    }
}
