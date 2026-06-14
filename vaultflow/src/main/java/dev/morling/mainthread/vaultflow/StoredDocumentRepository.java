package dev.morling.mainthread.vaultflow;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StoredDocumentRepository implements PanacheRepository<StoredDocument> {

    public Optional<StoredDocument> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }

    public List<StoredDocument> findByOwnerEmail(String ownerEmail) {
        return find("ownerEmail", ownerEmail).list();
    }
}
