package com.themainthread.shipment;

public record Shipment(
        String id,
        String description,
        String destinationCity,
        String warehouseCode,
        ShipmentStatus status) {
}
