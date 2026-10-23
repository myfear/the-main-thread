package com.themainthread.poolfaults;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "saved_quote")
public class SavedQuote {
    @Id
    public UUID id;

    @Column(nullable = false)
    public String destination;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;

    protected SavedQuote() {
    }

    public SavedQuote(String destination, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.destination = destination;
        this.amount = amount;
    }
}
