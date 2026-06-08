package dev.themainthread.shipments.api;

public record CreateShipmentRequest(
        String trackingNumber,
        String recipient,
        String destinationCity) {
}
