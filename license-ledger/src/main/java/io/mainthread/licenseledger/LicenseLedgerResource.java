package io.mainthread.licenseledger;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/components")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LicenseLedgerResource {

    private final ComponentCatalogService service;

    public LicenseLedgerResource(ComponentCatalogService service) {
        this.service = service;
    }

    @GET
    @Path("/demo")
    public List<ComponentReport> demo() {
        return service.sampleComponents();
    }

    @POST
    @Path("/review")
    public ComponentReport review(ComponentRequest request) {
        return service.review(request);
    }
}
