package dev.morling.mainthread.vaultflow;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DocumentResource {

    @Inject
    DocumentService service;

    @POST
    public Response create(CreateDocumentRequest request) {
        DocumentResponse response = service.create(request);
        return Response.created(URI.create("/documents/" + response.externalId()))
                .entity(response)
                .build();
    }

    @GET
    @Path("/{externalId}")
    public DocumentResponse getByExternalId(@PathParam("externalId") String externalId) {
        return service.getByExternalId(externalId);
    }

    @GET
    @Path("/search")
    public List<DocumentResponse> searchByOwnerEmail(@QueryParam("ownerEmail") String ownerEmail) {
        return service.searchByOwnerEmail(ownerEmail);
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapDuplicate(DuplicateDocumentException e) {
        return RestResponse.status(Response.Status.CONFLICT, Map.of("error", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapNotFound(DocumentNotFoundException e) {
        return RestResponse.status(Response.Status.NOT_FOUND, Map.of("error", e.getMessage()));
    }
}
