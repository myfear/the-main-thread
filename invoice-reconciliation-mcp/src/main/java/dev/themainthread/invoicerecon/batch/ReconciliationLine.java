package dev.themainthread.invoicerecon.batch;

import dev.themainthread.invoicerecon.domain.ReconciliationStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_line")
public class ReconciliationLine extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    public ReconciliationBatch batch;

    @Column(name = "invoice_id", nullable = false)
    public Long invoiceId;

    @Column(name = "invoice_number", nullable = false)
    public String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ReconciliationStatus status;
}
