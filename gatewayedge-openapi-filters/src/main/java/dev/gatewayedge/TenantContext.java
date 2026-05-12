package dev.gatewayedge;

import java.util.Locale;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TenantContext {

    private Tenant tenant = Tenant.BASIC;

    public void setFromHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            tenant = Tenant.BASIC;
            return;
        }
        tenant = switch (headerValue.trim().toLowerCase(Locale.ROOT)) {
            case "premium" -> Tenant.PREMIUM;
            default -> Tenant.BASIC;
        };
    }

    public Tenant current() {
        return tenant;
    }

    public boolean isBasic() {
        return tenant == Tenant.BASIC;
    }
}