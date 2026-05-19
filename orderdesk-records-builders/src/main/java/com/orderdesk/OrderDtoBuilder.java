package com.orderdesk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderDtoBuilder {

    private String orderId;
    private String customerId;
    private List<ProductDto> products = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal shipping = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String currency = "EUR";
    private String shippingAddress;
    private String billingAddress;
    private String status = "CREATED";
    private Integer fraudScore = 0;
    private Instant createdAt = Instant.now();

    public OrderDtoBuilder orderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public OrderDtoBuilder customerId(String customerId) {
        this.customerId = customerId;
        return this;
    }

    public OrderDtoBuilder addProduct(ProductDto product) {
        this.products.add(product);
        return this;
    }

    public OrderDtoBuilder subtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
        return this;
    }

    public OrderDtoBuilder tax(BigDecimal tax) {
        this.tax = tax;
        return this;
    }

    public OrderDtoBuilder shipping(BigDecimal shipping) {
        this.shipping = shipping;
        return this;
    }

    public OrderDtoBuilder total(BigDecimal total) {
        this.total = total;
        return this;
    }

    public OrderDtoBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    public OrderDtoBuilder shippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
        return this;
    }

    public OrderDtoBuilder billingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
        return this;
    }

    public OrderDtoBuilder status(String status) {
        this.status = status;
        return this;
    }

    public OrderDtoBuilder fraudScore(Integer fraudScore) {
        this.fraudScore = fraudScore;
        return this;
    }

    List<ProductDto> productsSnapshot() {
        return List.copyOf(products);
    }

    BigDecimal totalSnapshot() {
        return total;
    }

    public OrderDto build() {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("Customer ID is required");
        }

        if (products.isEmpty()) {
            throw new IllegalStateException("At least one product is required");
        }

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException("Order ID is required");
        }

        return new OrderDto(
                orderId,
                customerId,
                List.copyOf(products),
                subtotal,
                tax,
                shipping,
                total,
                currency,
                shippingAddress,
                billingAddress,
                status,
                fraudScore,
                createdAt);
    }
}
