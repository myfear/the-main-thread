package com.themainthread.swiftship;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "shipments")
public class Shipment extends PanacheEntityBase {

    @Id
    @Column(name = "tracking_number", length = 24, nullable = false)
    public String trackingNumber;

    @Column(length = 120, nullable = false)
    public String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 32, nullable = false)
    public ShipmentStatus currentStatus;

    @Column(name = "current_location", length = 120, nullable = false)
    public String currentLocation;

    @Column(name = "estimated_delivery", nullable = false)
    public LocalDate estimatedDelivery;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public long version;

    protected Shipment() {
    }
}
