package com.catalogapi.json;

public record ProductInput(String sku, String name, int priceCents, String category) {
}
