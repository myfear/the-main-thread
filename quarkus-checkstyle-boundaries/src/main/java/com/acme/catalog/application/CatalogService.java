package com.acme.catalog.application;

import java.util.Optional;

import com.acme.catalog.domain.Product;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatalogService {

    private final ProductCatalog productCatalog;

    public CatalogService(ProductCatalog productCatalog) {
        this.productCatalog = productCatalog;
    }

    public Optional<Product> findProduct(String sku) {
        return productCatalog.findBySku(sku);
    }
}
