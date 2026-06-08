package dev.themainthread.shipments.model;

public record Shipment(
        long id,
        String trackingNumber,
        String recipient,
        String destinationCity,
        ShipmentStatus status) {

    public Shipment withStatus(ShipmentStatus newStatus) {
        return new Shipment(id, trackingNumber, recipient, destinationCity, newStatus);
    }
}
