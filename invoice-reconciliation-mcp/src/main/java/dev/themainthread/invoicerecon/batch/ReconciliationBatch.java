package dev.themainthread.invoicerecon.batch;

import java.time.LocalDate;

import dev.themainthread.invoicerecon.domain.BatchStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_batch")
public class ReconciliationBatch extends PanacheEntity {

    @Column(name = "batch_id", nullable = false, unique = true)
    public String batchId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    public String idempotencyKey;

    @Column(name = "supplier_id", nullable = false)
    public String supplierId;

    @Column(name = "from_date", nullable = false)
    public LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    public LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BatchStatus status;

    @Column(nullable = false)
    public int processed;

    @Column(nullable = false)
    public int matched;

    @Column(nullable = false)
    public int exceptions;

    public static ReconciliationBatch findByBatchId(String batchId) {
        return find("batchId", batchId).firstResult();
    }

    public static ReconciliationBatch findByIdempotencyKey(String idempotencyKey) {
        return find("idempotencyKey", idempotencyKey).firstResult();
    }
}
