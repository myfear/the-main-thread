package com.themainthread.checkout;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 40)
    public String sku;

    @Column(nullable = false)
    public int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OrderStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static PurchaseOrder accepted(String sku, int quantity) {
        PurchaseOrder order = new PurchaseOrder();
        order.sku = sku;
        order.quantity = quantity;
        order.status = OrderStatus.ACCEPTED;
        order.createdAt = Instant.now();
        order.persist();
        return order;
    }
}
