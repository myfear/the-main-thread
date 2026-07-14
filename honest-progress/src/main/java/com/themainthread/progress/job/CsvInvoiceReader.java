package com.themainthread.progress.job;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.themainthread.progress.domain.InvoiceRow;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CsvInvoiceReader {

    private static final String EXPECTED_HEADER = "invoice_number,amount,currency";

    public long validateAndCount(Path path) throws IOException {
        Set<String> invoiceNumbers = new HashSet<>();
        long rowCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            requireHeader(reader.readLine());
            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                InvoiceRow row = parse(line, lineNumber);
                if (!invoiceNumbers.add(row.invoiceNumber())) {
                    throw new IllegalArgumentException("Duplicate invoice number at line " + lineNumber + ": "
                            + row.invoiceNumber());
                }
                rowCount++;
            }
        }
        if (rowCount == 0) {
            throw new IllegalArgumentException("The CSV contains no invoice rows");
        }
        return rowCount;
    }

    public void read(Path path, Consumer<InvoiceRow> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            requireHeader(reader.readLine());
            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.isBlank()) {
                    consumer.accept(parse(line, lineNumber));
                }
            }
        }
    }

    private void requireHeader(String header) {
        if (!EXPECTED_HEADER.equals(header)) {
            throw new IllegalArgumentException("Expected CSV header: " + EXPECTED_HEADER);
        }
    }

    private InvoiceRow parse(String line, long lineNumber) {
        String[] columns = line.split(",", -1);
        if (columns.length != 3) {
            throw new IllegalArgumentException("Expected three columns at line " + lineNumber);
        }

        String invoiceNumber = columns[0].trim();
        if (!invoiceNumber.matches("[A-Za-z0-9-]{1,40}")) {
            throw new IllegalArgumentException("Invalid invoice number at line " + lineNumber);
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(columns[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount at line " + lineNumber);
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive at line " + lineNumber);
        }

        String currency = columns[2].trim().toUpperCase();
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid currency at line " + lineNumber);
        }
        return new InvoiceRow(invoiceNumber, amount, currency);
    }
}
