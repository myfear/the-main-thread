package com.themainthread.decisions;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class DemoTools {
    enum Tool {
        getOrder("READ_ONLY", "Read an order's details. Does not refund money or change the order.",
                ToolDecisions.Intent.ORDER_LOOKUP),
        issueRefund("MUTATING", "Refund exactly amountCents euro cents for orderId. Changes the refund ledger.",
                ToolDecisions.Intent.REFUND);

        final String sideEffect;
        final String description;
        final ToolDecisions.Intent expectedIntent;

        Tool(String sideEffect, String description, ToolDecisions.Intent expectedIntent) {
            this.sideEffect = sideEffect;
            this.description = description;
            this.expectedIntent = expectedIntent;
        }
    }

    record Order(String id, String customerId, long paidCents) {
        Map<String, Object> modelInput() {
            return Map.of("orderId", id, "paidCents", paidCents, "currency", "EUR");
        }
    }

    record Invocation(String userRequest, Tool tool, Map<String, Object> arguments, Order order) {
        long amountCents() {
            return tool == Tool.issueRefund ? (Long) arguments.get("amountCents") : 0;
        }
    }

    private final Map<String, Order> orders = Map.of(
            "4711", new Order("4711", "alice", 49900),
            "4712", new Order("4712", "alice", 1999),
            "8421", new Order("8421", "bob", 7999));
    private final Map<String, Long> refunds = new ConcurrentHashMap<>();

    Invocation validate(ReviewRequest request) {
        if (request == null || request.userRequest() == null || request.userRequest().isBlank()
                || request.userRequest().length() > 2000) {
            throw new IllegalArgumentException("userRequest must contain 1 to 2000 characters");
        }
        Tool tool;
        try {
            tool = Tool.valueOf(request.toolName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown tool");
        }
        Map<String, Object> args = request.arguments();
        Set<String> keys = tool == Tool.getOrder ? Set.of("orderId") : Set.of("orderId", "amountCents");
        if (args == null || !args.keySet().equals(keys) || !(args.get("orderId") instanceof String id)) {
            throw new IllegalArgumentException("Arguments must match the tool's schema exactly");
        }
        Order order = orders.get(id);
        // This local demo has one fixed customer. A real service uses the authenticated principal.
        if (order == null || !order.customerId().equals("alice")) {
            throw new IllegalArgumentException("Order is not available to the demo customer");
        }
        if (tool == Tool.getOrder) {
            return new Invocation(request.userRequest(), tool, Map.of("orderId", id), order);
        }
        long amount;
        try {
            if (!(args.get("amountCents") instanceof Number number)) {
                throw new NumberFormatException();
            }
            amount = new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("amountCents must be an integer");
        }
        if (amount <= 0 || amount > order.paidCents()) {
            throw new IllegalArgumentException("Refund must be positive and cannot exceed the paid amount");
        }
        return new Invocation(request.userRequest(), tool, Map.of("orderId", id, "amountCents", amount), order);
    }

    Object execute(Invocation invocation) {
        if (invocation.tool() == Tool.getOrder) {
            return invocation.order().modelInput();
        }
        if (refunds.putIfAbsent(invocation.order().id(), invocation.amountCents()) != null) {
            throw new WebApplicationException("This demo allows one simulated refund per order", 409);
        }
        return Map.of("orderId", invocation.order().id(), "refundedCents", invocation.amountCents(), "simulated", true);
    }

    public Map<String, Long> refunds() {
        return Map.copyOf(refunds);
    }

    void reset() {
        refunds.clear();
    }
}
