package com.example.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IdempotencyService {

    @Transactional
    public Optional<IdempotencyRecord> find(String key) {
        return IdempotencyRecord.find("key", key).firstResultOptional();
    }

    @Transactional
    public IdempotencyRecord findOrCreate(
            String key,
            String requestFingerprint,
            Supplier<ProcessedResponse> processor) {

        try {
            ProcessedResponse response = processor.get();

            IdempotencyRecord record = new IdempotencyRecord();
            record.key = key;
            record.requestFingerprint = requestFingerprint;
            record.statusCode = response.status();
            record.responseBody = response.body();
            record.createdAt = Instant.now();
            record.expiresAt = Instant.now().plusSeconds(24 * 3600);

            record.persist();
            return record;

        } catch (PersistenceException e) {
            if (isConstraintViolation(e)) {
                return find(key).orElseThrow(() -> new IllegalStateException("Key exists but record not found"));
            }
            throw e;
        }
    }

    private boolean isConstraintViolation(PersistenceException e) {
        return e.getCause() instanceof org.hibernate.exception.ConstraintViolationException;
    }
}