package com.acme.catalog.adapter.rest;

import java.math.BigDecimal;

import com.acme.catalog.domain.Product;

public record ProductResponse(String sku, String name, BigDecimal price, String currency) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.sku(), product.name(), product.price(), product.currency().getCurrencyCode());
    }
}
