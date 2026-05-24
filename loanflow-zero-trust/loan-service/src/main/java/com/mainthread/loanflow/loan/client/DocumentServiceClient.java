package com.mainthread.loanflow.loan.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.mainthread.loanflow.loan.dto.DocumentWriteRequest;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/internal/documents")
@RegisterRestClient(configKey = "document-service")
@OidcClientFilter("internal-calls")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DocumentServiceClient {

    @POST
    Response write(DocumentWriteRequest request);
}
