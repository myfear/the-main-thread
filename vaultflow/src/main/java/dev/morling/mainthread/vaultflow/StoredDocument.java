package dev.morling.mainthread.vaultflow;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stored_documents")
public class StoredDocument extends PanacheEntity {

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    public String externalId;

    @Column(name = "owner_email", nullable = false, length = 256)
    public String ownerEmail;

    @Column(nullable = false, length = 256)
    public String title;

    @Column(name = "storage_key", nullable = false, length = 512)
    public String storageKey;

    @Column(nullable = false, length = 128)
    public String checksum;
}
