package com.themainthread.checkout;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CheckoutService {

    private final ObjectMapper json;
    private final CrashSwitch crashes;

    CheckoutService(ObjectMapper json, CrashSwitch crashes) {
        this.json = json;
        this.crashes = crashes;
    }

    public PurchaseOrder place(CheckoutRequest request, CrashPoint crashAfter) {
        crashes.arm(crashAfter);
        PurchaseOrder order = persistOrderAndOutbox(request);
        crashes.maybeCrash(CrashPoint.COMMIT);
        return order;
    }

    @Transactional
    PurchaseOrder persistOrderAndOutbox(CheckoutRequest request) {
        PurchaseOrder order = PurchaseOrder.accepted(request.sku(), request.quantity());
        OrderPlaced event = OrderPlaced.of(order, UUID.randomUUID());
        OutboxEvent.enqueue(event, json);
        return order;
    }
}
