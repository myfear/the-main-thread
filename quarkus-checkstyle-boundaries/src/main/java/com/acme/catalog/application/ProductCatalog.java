package com.acme.catalog.application;

import java.util.Optional;

import com.acme.catalog.domain.Product;

public interface ProductCatalog {

    Optional<Product> findBySku(String sku);
}
