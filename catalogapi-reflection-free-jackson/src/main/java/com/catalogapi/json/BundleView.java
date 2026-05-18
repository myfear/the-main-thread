package com.catalogapi.json;

import java.util.List;

public record BundleView(String name, List<String> skuList, Money totalPrice) implements CatalogPayload {
}
