package com.themainthread.progress;

import com.themainthread.progress.persistence.ImportJobRepository;
import com.themainthread.progress.persistence.InvoiceRecordRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataCleaner {

    private final ImportJobRepository jobs;
    private final InvoiceRecordRepository invoices;

    public TestDataCleaner(ImportJobRepository jobs, InvoiceRecordRepository invoices) {
        this.jobs = jobs;
        this.invoices = invoices;
    }

    @Transactional
    public void clean() {
        invoices.deleteAll();
        jobs.deleteAll();
    }
}
