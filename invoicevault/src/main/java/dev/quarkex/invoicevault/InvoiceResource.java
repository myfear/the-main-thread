package dev.quarkex.invoicevault;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/invoices")
public class InvoiceResource {

    @Inject
    InvoiceService invoiceService;

    @POST
    @Path("/{customerId}/{invoiceId}")
    @Consumes("application/pdf")
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(
            @PathParam("customerId") String customerId,
            @PathParam("invoiceId") String invoiceId,
            byte[] body) {
        String key = invoiceService.store(invoiceId, body, customerId);
        return Response.ok(Map.of("key", key)).build();
    }
}