package dev.themainthread.wallet;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

@Path("/wallet")
public class WalletResource {

    private final PassTemplateService passTemplateService;

    WalletResource(PassTemplateService passTemplateService) {
        this.passTemplateService = passTemplateService;
    }

    @GET
    @Path("/membership/{memberId:[A-Za-z0-9_-]+}/template")
    @Produces("application/zip")
    public Response template(
            @PathParam("memberId") String memberId,
            @QueryParam("name") @DefaultValue("Main Thread Reader") String name,
            @QueryParam("tier") @DefaultValue("Member") String tier) throws IOException {
        byte[] templateBytes = passTemplateService.createTemplate(memberId, name, tier);

        return Response.ok(templateBytes)
                .header("Content-Disposition", "attachment; filename=\"main-thread-" + memberId
                        + ".pkpasstemplate.zip\"")
                .build();
    }
}
