package com.themainthread.fernbank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillManifest(
        @JsonProperty("skill_id") String skillId,
        String publisher,
        @JsonProperty("publisher_trust_tier") String publisherTrustTier,
        @JsonProperty("signature_verified") boolean signatureVerified,
        @JsonProperty("requested_scopes") List<String> requestedScopes,
        @JsonProperty("declared_capabilities") List<String> declaredCapabilities,
        @JsonProperty("allowed_teams") List<String> allowedTeams) {
}
