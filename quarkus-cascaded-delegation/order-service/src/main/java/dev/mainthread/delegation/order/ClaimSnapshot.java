package dev.mainthread.delegation.order;

import java.util.Comparator;
import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;

public record ClaimSnapshot(
        String service,
        String subject,
        String username,
        List<String> audience,
        String authorizedParty,
        String scope,
        String tokenId,
        String correlationId) {

    public ClaimSnapshot {
        audience = List.copyOf(audience);
    }

    public static ClaimSnapshot from(String service, JsonWebToken token, String correlationId) {
        List<String> audience = token.getAudience() == null
                ? List.of()
                : token.getAudience().stream().sorted(Comparator.naturalOrder()).toList();

        return new ClaimSnapshot(
                service,
                token.getSubject(),
                token.getClaim("preferred_username"),
                audience,
                token.getClaim("azp"),
                token.getClaim("scope"),
                token.getTokenID(),
                correlationId);
    }
}
