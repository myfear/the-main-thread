package com.orderdesk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderAssemblyService {

    private static final Logger LOG = Logger.getLogger(OrderAssemblyService.class);

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal SHIPPING_FLAT = new BigDecimal("9.99");

    private final ProductCatalog catalog;

    public OrderAssemblyService(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    public OrderDto buildSampleOrder(String orderId) {
        ProductDto keyboard = catalog.findById(1L).orElseThrow();
        ProductDto mouse = catalog.findById(2L).orElseThrow();

        OrderDtoBuilder builder = new OrderDtoBuilder()
                .orderId(orderId)
                .customerId("customer-42")
                .addProduct(keyboard)
                .addProduct(mouse)
                .shippingAddress("Main Street 10")
                .billingAddress("Main Street 10");

        applyInventory(builder);
        applyPricing(builder);
        applyShipping(builder);
        applyFraud(builder);

        return builder.build();
    }

    public OrderDto assembleFromRequest(CreateOrderRequest request) {
        OrderDtoBuilder builder = new OrderDtoBuilder()
                .orderId(UUID.randomUUID().toString())
                .customerId(request.customerId())
                .shippingAddress(request.shippingAddress())
                .billingAddress(request.shippingAddress());

        for (Long productId : request.productIds()) {
            ProductDto product = catalog.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
            builder.addProduct(product);
        }

        applyInventory(builder);
        applyPricing(builder);
        applyShipping(builder);
        applyFraud(builder);

        OrderDto order = builder.build();
        LOG.infof("Assembled order %s for customer %s", order.orderId(), order.customerId());
        return order;
    }

    private void applyInventory(OrderDtoBuilder builder) {
        builder.status("INVENTORY_CONFIRMED");
        LOG.debug("Inventory enrichment applied");
    }

    private void applyPricing(OrderDtoBuilder builder) {
        BigDecimal subtotal = builder.productsSnapshot().stream()
                .map(ProductDto::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBeforeShipping = subtotal.add(tax);

        builder.subtotal(subtotal).tax(tax).total(totalBeforeShipping).status("PRICED");
        LOG.debugf("Pricing enrichment applied: subtotal=%s tax=%s", subtotal, tax);
    }

    private void applyShipping(OrderDtoBuilder builder) {
        BigDecimal totalWithShipping = builder.totalSnapshot().add(SHIPPING_FLAT);
        builder.shipping(SHIPPING_FLAT).total(totalWithShipping).status("SHIPPING_QUOTED");
        LOG.debug("Shipping enrichment applied");
    }

    private void applyFraud(OrderDtoBuilder builder) {
        int score = builder.totalSnapshot().compareTo(new BigDecimal("200")) > 0 ? 12 : 5;
        builder.fraudScore(score).status("READY");
        LOG.debugf("Fraud enrichment applied: score=%d", score);
    }
}
