package com.orderdesk;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductCatalog {

    private static final Map<Long, ProductDto> PRODUCTS = Map.of(
            1L, new ProductDto(1L, "Mechanical Keyboard", new BigDecimal("149.99")),
            2L, new ProductDto(2L, "Vertical Mouse", new BigDecimal("89.99")));

    public List<ProductDto> listAll() {
        return PRODUCTS.keySet().stream()
                .sorted()
                .map(PRODUCTS::get)
                .toList();
    }

    public Optional<ProductDto> findById(long id) {
        return Optional.ofNullable(PRODUCTS.get(id));
    }
}
