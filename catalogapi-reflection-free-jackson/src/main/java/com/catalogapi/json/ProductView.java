package com.catalogapi.json;

public record ProductView(long id, String sku, Money price) implements CatalogPayload {
}
