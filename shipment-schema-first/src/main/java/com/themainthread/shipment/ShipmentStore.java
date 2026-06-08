package com.themainthread.shipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ShipmentStore {

    public static final String DEFAULT_WAREHOUSE = "BER";

    private final Map<String, Shipment> shipments = new ConcurrentHashMap<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    void seed(@Observes StartupEvent event) {
        shipments.put("BER-1001", new Shipment(
                "BER-1001",
                "Laptop spare parts for the Berlin repair hub",
                "Berlin",
                "BER",
                ShipmentStatus.CREATED));
        shipments.put("BER-1002", new Shipment(
                "BER-1002",
                "Priority passport package for the embassy desk",
                "Berlin",
                "BER",
                ShipmentStatus.IN_TRANSIT));
        shipments.put("AMS-2001", new Shipment(
                "AMS-2001",
                "Cold-chain insulin refill for Amsterdam clinic",
                "Amsterdam",
                "AMS",
                ShipmentStatus.PICKED));
    }

    public List<Shipment> listShipments(String warehouseCode, ShipmentStatus status) {
        List<Shipment> visible = new ArrayList<>();
        for (Shipment shipment : shipments.values()) {
            if (!shipment.warehouseCode().equals(warehouseCode)) {
                continue;
            }
            if (status != null && shipment.status() != status) {
                continue;
            }
            visible.add(shipment);
        }
        return visible;
    }

    public Shipment findShipment(String id, String warehouseCode) {
        Shipment shipment = shipments.get(id);
        if (shipment == null || !shipment.warehouseCode().equals(warehouseCode)) {
            return null;
        }
        return shipment;
    }

    public Shipment updateStatus(String id, ShipmentStatus status, String warehouseCode) {
        Shipment current = findShipment(id, warehouseCode);
        if (current == null) {
            throw new NoSuchElementException("Shipment " + id + " is not visible from warehouse " + warehouseCode);
        }

        Shipment updated = new Shipment(
                current.id(),
                current.description(),
                current.destinationCity(),
                current.warehouseCode(),
                status);
        shipments.put(id, updated);
        broadcast(updated);
        return updated;
    }

    public Multi<Shipment> shipmentUpdates(String id) {
        return Multi.createFrom().<Shipment> emitter(emitter -> {
            Subscription subscription = new Subscription(id, emitter);
            subscriptions.add(subscription);
            emitter.onTermination(() -> subscriptions.remove(subscription));
        });
    }

    private void broadcast(Shipment shipment) {
        for (Subscription subscription : subscriptions) {
            if (subscription.shipmentId.equals(shipment.id())) {
                subscription.emitter.emit(shipment);
            }
        }
    }

    private static final class Subscription {
        private final String shipmentId;
        private final MultiEmitter<? super Shipment> emitter;

        private Subscription(String shipmentId, MultiEmitter<? super Shipment> emitter) {
            this.shipmentId = shipmentId;
            this.emitter = emitter;
        }
    }
}
