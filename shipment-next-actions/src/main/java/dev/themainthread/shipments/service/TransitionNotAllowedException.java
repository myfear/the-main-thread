package dev.themainthread.shipments.service;

import dev.themainthread.shipments.model.Shipment;

public class TransitionNotAllowedException extends RuntimeException {

    public TransitionNotAllowedException(Shipment shipment, String action) {
        super("Shipment %d in status %s cannot %s"
                .formatted(shipment.id(), shipment.status(), action));
    }
}
