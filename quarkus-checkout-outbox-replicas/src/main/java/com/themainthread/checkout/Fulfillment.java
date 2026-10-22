package com.themainthread.checkout;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fulfillment")
public class Fulfillment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    public UUID eventId;

    @Column(name = "order_id", nullable = false)
    public long orderId;

    @Column(nullable = false, length = 40)
    public String sku;

    @Column(nullable = false)
    public int quantity;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static Fulfillment findByOrderId(long orderId) {
        return find("orderId", orderId).firstResult();
    }
}
