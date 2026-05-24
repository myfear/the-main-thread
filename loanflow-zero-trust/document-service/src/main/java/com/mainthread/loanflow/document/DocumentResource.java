package com.mainthread.loanflow.document;

import com.mainthread.loanflow.document.dto.DocumentWriteRequest;
import com.mainthread.loanflow.document.dto.StoredDocument;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DocumentResource {

    @Inject
    DocumentStore documentStore;

    @Inject
    SecurityIdentity identity;

    @POST
    @PermissionsAllowed("document_write")
    public StoredDocument write(DocumentWriteRequest request) {
        return documentStore.store(request, identity.getPrincipal().getName());
    }
}
