package com.themainthread.reservation;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class InventoryService {

    static final String LEDGER_OFFLINE_SKU = "ledger-offline";
    static final URI INSUFFICIENT_STOCK_TYPE = URI
            .create("https://errors.example.com/insufficient-stock");

    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "keyboard-1", 2,
            "mouse-1", 10));

    public Reservation reserve(String sku, int quantity) {
        if (LEDGER_OFFLINE_SKU.equals(sku)) {
            throw new IllegalStateException("Inventory ledger is unreachable");
        }

        Integer available = stock.get(sku);
        if (available == null) {
            throw new NotFoundException("Unknown SKU: " + sku);
        }
        if (quantity > available) {
            throw HttpProblem.builder()
                    .withType(INSUFFICIENT_STOCK_TYPE)
                    .withTitle("Insufficient stock")
                    .withStatus(Response.Status.CONFLICT)
                    .withDetail("The requested quantity is no longer available.")
                    .with("sku", sku)
                    .with("requested", quantity)
                    .with("available", available)
                    .build();
        }

        int remaining = available - quantity;
        stock.put(sku, remaining);
        return new Reservation(sku, quantity, remaining);
    }
}
