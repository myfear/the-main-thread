package com.catalogapi.json;

public record ProductSummary(long id, String sku, String name, Money price) {
}
