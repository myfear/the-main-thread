package io.mainthread.vaultboard.dashboard;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class HostTenantTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.multi-tenant.orm.header-filter.enabled", "false");
    }
}
