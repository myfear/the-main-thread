package dev.gatewayedge;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

/**
 * Runs on the Vert.x pipeline so tenant selection applies to {@code /q/openapi} as well as JAX-RS
 * resources (a Jakarta REST {@code ContainerRequestFilter} is not guaranteed to see non-resource
 * routes).
 */
@ApplicationScoped
public class TenantRouteFilter {

    private final TenantContext tenantContext;

    TenantRouteFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @RouteFilter(1)
    void tenant(RoutingContext routingContext) {
        tenantContext.setFromHeader(routingContext.request().getHeader("X-Gateway-Tenant"));
        routingContext.next();
    }
}