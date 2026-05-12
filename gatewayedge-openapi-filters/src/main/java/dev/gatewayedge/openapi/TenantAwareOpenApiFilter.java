package dev.gatewayedge.openapi;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;

import dev.gatewayedge.TenantContext;
import io.quarkus.smallrye.openapi.OpenApiFilter;

@OpenApiFilter(stages = OpenApiFilter.RunStage.RUNTIME_PER_REQUEST)
@ApplicationScoped
public class TenantAwareOpenApiFilter implements OASFilter {

    static final String PREMIUM_REPORT_PATH = "/api/premium/report";

    private final TenantContext tenantContext;

    TenantAwareOpenApiFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (!tenantContext.isBasic()) {
            return;
        }
        if (openAPI.getPaths() != null) {
            openAPI.getPaths().removePathItem(PREMIUM_REPORT_PATH);
        }
    }
}