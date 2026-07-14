package com.themainthread.swiftship;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public List<ShipmentView> list() {
        List<ShipmentView> result = new ArrayList<>();
        for (Shipment shipment : shipmentRepository.listByTrackingNumber()) {
            result.add(ShipmentView.from(shipment));
        }
        return List.copyOf(result);
    }

    public Optional<ShipmentView> find(String trackingNumber) {
        return shipmentRepository.findByIdOptional(trackingNumber).map(ShipmentView::from);
    }

    public ShipmentSummary summary() {
        Map<ShipmentStatus, Long> counts = new EnumMap<>(ShipmentStatus.class);
        for (ShipmentStatus shipmentStatus : ShipmentStatus.values()) {
            counts.put(shipmentStatus, 0L);
        }

        List<Shipment> shipments = shipmentRepository.listAll();
        for (Shipment shipment : shipments) {
            counts.compute(shipment.currentStatus, (status, count) -> count + 1);
        }
        return new ShipmentSummary(shipments.size(), Map.copyOf(counts));
    }
}
