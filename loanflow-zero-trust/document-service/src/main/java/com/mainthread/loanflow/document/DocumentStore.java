package com.mainthread.loanflow.document;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mainthread.loanflow.document.dto.DocumentWriteRequest;
import com.mainthread.loanflow.document.dto.StoredDocument;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DocumentStore {

    private static final Logger LOG = Logger.getLogger(DocumentStore.class);

    private final Map<String, StoredDocument> documentsById = new ConcurrentHashMap<>();

    public StoredDocument store(DocumentWriteRequest request, String caller) {
        StoredDocument document = new StoredDocument(
                UUID.randomUUID().toString(),
                request.loanId(),
                request.submittedBy(),
                request.branch(),
                request.creditBand(),
                request.submittedAt());
        documentsById.put(document.id(), document);
        LOG.infof(
                "Stored audit document loanId=%s branch=%s creditBand=%s caller=%s",
                request.loanId(),
                request.branch(),
                request.creditBand(),
                caller);
        return document;
    }

    public Optional<StoredDocument> findById(String id) {
        return Optional.ofNullable(documentsById.get(id));
    }
}
