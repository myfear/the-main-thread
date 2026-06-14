package dev.morling.mainthread.vaultflow;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DocumentService {

    @Inject
    StoredDocumentRepository repository;

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        repository.findByExternalId(request.externalId())
                .ifPresent(existing -> {
                    throw new DuplicateDocumentException(request.externalId());
                });

        StoredDocument document = new StoredDocument();
        document.externalId = request.externalId();
        document.ownerEmail = request.ownerEmail();
        document.title = request.title();
        document.storageKey = request.storageKey();
        document.checksum = request.checksum();
        repository.persist(document);

        return toResponse(document);
    }

    public DocumentResponse getByExternalId(String externalId) {
        StoredDocument document = repository.findByExternalId(externalId)
                .orElseThrow(() -> new DocumentNotFoundException(externalId));
        return toResponse(document);
    }

    public List<DocumentResponse> searchByOwnerEmail(String ownerEmail) {
        return repository.findByOwnerEmail(ownerEmail).stream()
                .map(DocumentService::toResponse)
                .toList();
    }

    private static DocumentResponse toResponse(StoredDocument document) {
        return new DocumentResponse(
                document.id,
                document.externalId,
                document.ownerEmail,
                document.title,
                document.storageKey,
                document.checksum);
    }
}
