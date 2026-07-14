package com.themainthread.progress.persistence;

import com.themainthread.progress.domain.InvoiceRecord;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InvoiceRecordRepository implements PanacheRepository<InvoiceRecord> {
}
