package dev.themainthread.invoicerecon.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BatchIdGenerator {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final AtomicInteger sequence = new AtomicInteger(1);

    public String nextBatchId() {
        LocalDate today = LocalDate.now();
        return "REC-" + today.format(MONTH_FORMAT) + "-" + String.format("%04d", sequence.getAndIncrement());
    }
}
