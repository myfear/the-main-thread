package com.example.flags;

import java.util.HashMap;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeatureFlagInitializer {

    @Startup
    @Transactional
    void init() {
        FeatureFlag.deleteAll();

        var premium = new FeatureFlag();
        premium.feature = "premium-features";
        premium.value = "true";
        premium.metadata = new HashMap<>();
        premium.metadata.put("owner", "platform-team");
        premium.metadata.put("status", "active");
        premium.metadata.put("category", "feature");
        premium.metadata.put("tags", "api,premium,billing");
        premium.metadata.put("riskLevel", "medium");
        premium.metadata.put("documentationUrl", "https://wiki.example.com/premium-features");
        premium.description = "Expose premium API fields";
        premium.persist();

        var bulk = new FeatureFlag();
        bulk.feature = "bulk-operations";
        bulk.value = "false";
        bulk.metadata = new HashMap<>();
        bulk.description = "Enable bulk updates";
        bulk.persist();
    }
}