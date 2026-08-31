package com.acme.catalog.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Product(String sku, String name, BigDecimal price, Currency currency) {

    public Product {
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }
}
