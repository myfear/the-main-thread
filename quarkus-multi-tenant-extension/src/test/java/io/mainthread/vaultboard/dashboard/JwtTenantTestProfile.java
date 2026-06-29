package io.mainthread.vaultboard.dashboard;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class JwtTenantTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.multi-tenant.http.strategy", "jwt",
                "quarkus.multi-tenant.orm.header-filter.enabled", "false",
                "mp.jwt.verify.publickey.location", "publicKey.pem",
                "mp.jwt.verify.publickey.algorithm", "RS256",
                "mp.jwt.verify.issuer", "https://auth.vaultboard.example",
                "smallrye.jwt.sign.key.location", "privateKey.pem");
    }
}
