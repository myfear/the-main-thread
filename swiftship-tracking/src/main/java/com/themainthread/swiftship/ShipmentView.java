package com.themainthread.swiftship;

import java.time.Instant;
import java.time.LocalDate;

public record ShipmentView(
        String trackingNumber,
        String destination,
        ShipmentStatus status,
        String currentLocation,
        LocalDate estimatedDelivery,
        Instant updatedAt) {

    static ShipmentView from(Shipment shipment) {
        return new ShipmentView(
                shipment.trackingNumber,
                shipment.destination,
                shipment.currentStatus,
                shipment.currentLocation,
                shipment.estimatedDelivery,
                shipment.updatedAt);
    }
}
