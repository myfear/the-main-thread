package com.themainthread.ledgerlock;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "totp_enrollment")
public class TotpEnrollment extends PanacheEntityBase {

    @Id
    @Column(nullable = false, updatable = false)
    public String subject;

    @Column(name = "vault_key", nullable = false, unique = true, updatable = false)
    public String vaultKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
