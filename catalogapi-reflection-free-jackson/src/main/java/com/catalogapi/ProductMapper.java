package com.catalogapi;

import com.catalogapi.json.Money;
import com.catalogapi.json.ProductSummary;

final class ProductMapper {

    private ProductMapper() {
    }

    static ProductSummary toSummary(Product product) {
        return new ProductSummary(
                product.id,
                product.sku,
                product.name,
                new Money("USD", product.priceCents));
    }

    static Money toMoney(Product product) {
        return new Money("USD", product.priceCents);
    }
}
