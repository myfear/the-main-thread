package io.mainthread.vaultboard.tenant;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.quarkiverse.multitenancy.core.runtime.api.TenantResolution;
import io.quarkiverse.multitenancy.core.runtime.api.TenantResolutionContext;
import io.quarkiverse.multitenancy.core.runtime.api.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

@ApplicationScoped
public class HostTenantResolver implements TenantResolver {

    private static final Set<String> KNOWN_TENANTS = Set.of("acme", "globex");

    @Override
    public TenantResolution resolve(TenantResolutionContext context) {
        Optional<ContainerRequestContext> request = context.get(ContainerRequestContext.class);
        if (request.isEmpty()) {
            return TenantResolution.notApplicable();
        }

        String hostHeader = request.get().getHeaderString("Host");
        if (hostHeader == null || hostHeader.isBlank()) {
            return TenantResolution.notApplicable();
        }

        String authority = hostHeader.trim().toLowerCase(Locale.ROOT);
        String host = authority.split(":", 2)[0];
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return TenantResolution.notApplicable();
        }

        int firstDot = host.indexOf('.');
        if (firstDot < 1) {
            return TenantResolution.notApplicable();
        }

        String tenant = host.substring(0, firstDot);
        if (!KNOWN_TENANTS.contains(tenant)) {
            return TenantResolution.rejected("Unknown tenant host: " + tenant);
        }

        return TenantResolution.resolved(tenant);
    }
}
