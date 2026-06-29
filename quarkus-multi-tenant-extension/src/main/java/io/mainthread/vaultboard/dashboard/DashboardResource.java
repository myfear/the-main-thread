package io.mainthread.vaultboard.dashboard;

import java.util.List;
import java.util.Map;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/dashboards")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    private final DashboardService dashboardService;
    private final TenantContext tenantContext;

    public DashboardResource(DashboardService dashboardService, TenantContext tenantContext) {
        this.dashboardService = dashboardService;
        this.tenantContext = tenantContext;
    }

    @GET
    public List<Dashboard> list() {
        return dashboardService.listAll();
    }

    @POST
    public Dashboard create(CreateDashboardRequest request) {
        return dashboardService.create(request);
    }

    @GET
    @Path("/tenant")
    public Map<String, String> currentTenant() {
        return Map.of("tenant", tenantContext.getTenantId().orElse("missing"));
    }
}
