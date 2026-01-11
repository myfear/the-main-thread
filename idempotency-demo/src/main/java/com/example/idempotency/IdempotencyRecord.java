package com.example.idempotency;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends PanacheEntity {

    @Column(name = "idem_key", unique = true, nullable = false)
    public String key;

    @Column(name = "request_fingerprint", nullable = false)
    public String requestFingerprint;

    @Column(name = "status_code", nullable = false)
    public int statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    public String responseBody;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at")
    public Instant expiresAt;
}