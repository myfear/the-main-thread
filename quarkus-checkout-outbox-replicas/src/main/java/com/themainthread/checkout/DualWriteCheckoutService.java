package com.themainthread.checkout;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DualWriteCheckoutService {

    private final OrderPlacedProducer producer;
    private final CrashSwitch crashes;

    DualWriteCheckoutService(OrderPlacedProducer producer, CrashSwitch crashes) {
        this.producer = producer;
        this.crashes = crashes;
    }

    public PurchaseOrder place(CheckoutRequest request, CrashPoint crashAfter) {
        crashes.arm(crashAfter);
        PurchaseOrder order = persistOrder(request);
        crashes.maybeCrash(CrashPoint.COMMIT);
        producer.send(OrderPlaced.of(order, UUID.randomUUID()));
        crashes.maybeCrash(CrashPoint.KAFKA);
        return order;
    }

    @Transactional
    PurchaseOrder persistOrder(CheckoutRequest request) {
        return PurchaseOrder.accepted(request.sku(), request.quantity());
    }
}
