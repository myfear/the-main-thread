package dev.themainthread.shipments.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.themainthread.shipments.api.CreateShipmentRequest;
import dev.themainthread.shipments.model.Shipment;
import dev.themainthread.shipments.model.ShipmentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ShipmentStore {

    private final Map<Long, Shipment> shipments = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public Shipment create(CreateShipmentRequest request) {
        long id = ids.incrementAndGet();
        Shipment shipment = new Shipment(
                id,
                request.trackingNumber(),
                request.recipient(),
                request.destinationCity(),
                ShipmentStatus.CREATED);
        shipments.put(id, shipment);
        return shipment;
    }

    public List<Shipment> list() {
        return shipments.values().stream()
                .sorted(Comparator.comparingLong(Shipment::id))
                .toList();
    }

    public Shipment get(long id) {
        Shipment shipment = shipments.get(id);
        if (shipment == null) {
            throw new NotFoundException("Shipment " + id + " was not found");
        }
        return shipment;
    }

    public Shipment update(Shipment shipment) {
        shipments.put(shipment.id(), shipment);
        return shipment;
    }

    public void clear() {
        shipments.clear();
        ids.set(0);
    }
}
