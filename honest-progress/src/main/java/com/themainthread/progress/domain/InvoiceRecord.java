package com.themainthread.progress.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "invoice_record", uniqueConstraints = @UniqueConstraint(name = "uk_invoice_job_number", columnNames = {
        "job_id", "invoice_number" }))
public class InvoiceRecord {

    @Id
    @GeneratedValue
    public Long id;

    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    @Column(name = "invoice_number", nullable = false, length = 40)
    public String invoiceNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false)
    public boolean published;
}
