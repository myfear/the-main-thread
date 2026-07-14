package com.themainthread.progress.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import com.themainthread.progress.domain.InvoiceRow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvInvoiceReaderTest {

    private final CsvInvoiceReader reader = new CsvInvoiceReader();

    @TempDir
    Path directory;

    @Test
    void validatesAndReadsRows() throws IOException {
        Path csv = write("""
                invoice_number,amount,currency
                INV-1,10.25,eur
                INV-2,20.00,USD
                """);

        assertEquals(2, reader.validateAndCount(csv));
        var rows = new ArrayList<InvoiceRow>();
        reader.read(csv, rows::add);
        assertEquals(2, rows.size());
        assertEquals("EUR", rows.getFirst().currency());
    }

    @Test
    void rejectsDuplicateInvoiceNumbersBeforeImporting() throws IOException {
        Path csv = write("""
                invoice_number,amount,currency
                INV-1,10.25,EUR
                INV-1,20.00,USD
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reader.validateAndCount(csv));
        assertEquals("Duplicate invoice number at line 3: INV-1", exception.getMessage());
    }

    @Test
    void rejectsAnUnexpectedHeader() throws IOException {
        Path csv = write("number,total,currency\nINV-1,10.25,EUR\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> reader.validateAndCount(csv));
        assertEquals("Expected CSV header: invoice_number,amount,currency", exception.getMessage());
    }

    private Path write(String content) throws IOException {
        Path file = directory.resolve("invoices.csv");
        return Files.writeString(file, content);
    }
}
