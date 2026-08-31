package com.acme.catalog.adapter.catalog;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

import com.acme.catalog.application.ProductCatalog;
import com.acme.catalog.domain.Product;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InMemoryProductCatalog implements ProductCatalog {

    private static final Currency EUR = Currency.getInstance("EUR");

    private final Map<String, Product> products = Map.of(
            "sku-1", new Product("sku-1", "Mechanical Keyboard", new BigDecimal("129.00"), EUR),
            "sku-2", new Product("sku-2", "USB-C Dock", new BigDecimal("89.00"), EUR));

    @Override
    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(products.get(sku));
    }
}
