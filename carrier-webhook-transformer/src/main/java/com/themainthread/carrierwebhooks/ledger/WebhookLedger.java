package com.themainthread.carrierwebhooks.ledger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.themainthread.carrierwebhooks.model.NormalizedShipment;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebhookLedger {

    private final ConcurrentMap<String, NormalizedShipment> accepted = new ConcurrentHashMap<>();

    public ProcessingResult record(NormalizedShipment shipment) {
        String key = shipment.carrier() + ":" + shipment.eventId();
        NormalizedShipment previous = accepted.putIfAbsent(key, shipment);
        return new ProcessingResult(previous != null);
    }
}
