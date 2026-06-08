package dev.themainthread.shipments.service;

import java.util.List;

import dev.themainthread.shipments.model.Shipment;
import dev.themainthread.shipments.model.ShipmentStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShipmentWorkflow {

    public Shipment pay(Shipment shipment) {
        requireStatus(shipment, ShipmentStatus.CREATED, "pay");
        return shipment.withStatus(ShipmentStatus.PAID);
    }

    public Shipment pack(Shipment shipment) {
        requireStatus(shipment, ShipmentStatus.PAID, "pack");
        return shipment.withStatus(ShipmentStatus.PACKED);
    }

    public Shipment ship(Shipment shipment) {
        requireStatus(shipment, ShipmentStatus.PACKED, "ship");
        return shipment.withStatus(ShipmentStatus.SHIPPED);
    }

    public Shipment deliver(Shipment shipment) {
        requireStatus(shipment, ShipmentStatus.SHIPPED, "deliver");
        return shipment.withStatus(ShipmentStatus.DELIVERED);
    }

    public Shipment cancel(Shipment shipment) {
        if (shipment.status() != ShipmentStatus.CREATED && shipment.status() != ShipmentStatus.PAID) {
            throw new TransitionNotAllowedException(shipment, "cancel");
        }
        return shipment.withStatus(ShipmentStatus.CANCELLED);
    }

    public List<String> allowedActions(Shipment shipment) {
        return switch (shipment.status()) {
            case CREATED -> List.of("pay", "cancel");
            case PAID -> List.of("pack", "cancel");
            case PACKED -> List.of("ship");
            case SHIPPED -> List.of("deliver");
            case DELIVERED, CANCELLED -> List.of();
        };
    }

    private void requireStatus(Shipment shipment, ShipmentStatus expected, String action) {
        if (shipment.status() != expected) {
            throw new TransitionNotAllowedException(shipment, action);
        }
    }
}
