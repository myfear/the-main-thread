package com.example.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.example.entity.Product;

import io.quarkiverse.flags.Flags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProductService {

    @Inject
    Flags flags;

    private final List<Product> products = Arrays.asList(
            new Product(1L, "Laptop", "Electronics", 999.99, "LAP-001"),
            new Product(2L, "Mouse", "Electronics", 29.99, "MOU-001"),
            new Product(3L, "Desk", "Furniture", 299.99, "DSK-001"),
            new Product(4L, "Chair", "Furniture", 199.99, "CHR-001"));

    public List<Product> getAllProducts() {
        return products;
    }

    public List<Product> getProductsWithDetails() {
        // Premium feature: includes SKU information
        if (flags.isEnabled("premium-features")) {
            return products;
        }

        // Basic response without SKU
        return products.stream()
                .map(p -> new Product(p.id, p.name, p.category, p.price, null))
                .collect(Collectors.toList());
    }

    public boolean canPerformBulkOperations() {
        return flags.isEnabled("bulk-operations");
    }

    public List<Product> bulkUpdatePrices(Double multiplier) {
        if (!canPerformBulkOperations()) {
            throw new IllegalStateException("Bulk operations feature is disabled");
        }

        return products.stream()
                .peek(p -> p.price = p.price * multiplier)
                .collect(Collectors.toList());
    }
}