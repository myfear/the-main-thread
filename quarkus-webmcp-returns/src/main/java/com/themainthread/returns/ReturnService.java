package com.themainthread.returns;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReturnService {
    private static final Order ORDER = new Order("ORD-1042", "EUR", List.of(
            new OrderItem("KEYBOARD", "Mechanical keyboard", 1, 8900, true),
            new OrderItem("CABLE", "USB-C cable", 2, 1900, true),
            new OrderItem("GIFT-CARD", "Digital gift card", 1, 2500, false)));
    private static final Set<String> REASONS = Set.of("damaged", "wrong_item", "changed_mind");

    public Order order(String orderId) {
        if (!ORDER.id().equals(orderId)) {
            throw new InvalidReturn("Order not found: " + orderId);
        }
        return ORDER;
    }

    public Draft preview(String orderId, ReturnRequest request) {
        Order order = order(orderId);
        if (request == null || request.items() == null || request.items().isEmpty()
                || request.items().size() > order.items().size()) {
            throw new InvalidReturn("Choose between one and three distinct order items.");
        }
        if (request.reason() == null || !REASONS.contains(request.reason())) {
            throw new InvalidReturn("Reason must be damaged, wrong_item, or changed_mind.");
        }
        Set<String> seen = new HashSet<>();
        List<DraftItem> items = new ArrayList<>();
        int total = 0;
        for (Selection selection : request.items()) {
            if (selection == null || selection.sku() == null || !seen.add(selection.sku())) {
                throw new InvalidReturn("Each item must have a distinct SKU from this order.");
            }
            OrderItem item = null;
            for (OrderItem candidate : order.items()) {
                if (candidate.sku().equals(selection.sku())) {
                    item = candidate;
                    break;
                }
            }
            if (item == null) {
                throw new InvalidReturn("SKU is not in this order: " + selection.sku());
            }
            if (!item.returnable()) {
                throw new InvalidReturn(item.name() + " is not returnable.");
            }
            if (selection.quantity() == null || selection.quantity() < 1
                    || selection.quantity() > item.quantity()) {
                throw new InvalidReturn("Quantity for " + item.sku() + " must be between 1 and " + item.quantity() + ".");
            }
            int refund = selection.quantity() * item.unitPriceCents();
            total += refund;
            items.add(new DraftItem(item.sku(), item.name(), selection.quantity(), refund));
        }
        return new Draft(order.id(), order.currency(), List.copyOf(items), request.reason(), total, "DRAFT", false);
    }

    public record Order(String id, String currency, List<OrderItem> items) {
    }

    public record OrderItem(String sku, String name, int quantity, int unitPriceCents, boolean returnable) {
    }

    public record Selection(String sku, Integer quantity) {
    }

    public record ReturnRequest(List<Selection> items, String reason) {
    }

    public record DraftItem(String sku, String name, int quantity, int refundCents) {
    }

    public record Draft(String orderId, String currency, List<DraftItem> items, String reason,
            int refundCents, String status, boolean submitted) {
    }

    public static class InvalidReturn extends RuntimeException {
        public InvalidReturn(String message) {
            super(message);
        }
    }
}
