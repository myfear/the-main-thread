package io.mainthread.catalogboard;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String category,
        int stock,
        int reorderPoint,
        boolean discontinued,
        boolean needsRestock) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id,
                product.sku,
                product.name,
                product.category,
                product.stock,
                product.reorderPoint,
                product.discontinued,
                product.needsRestock());
    }
}
