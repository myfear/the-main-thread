package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class DualWriteCheckoutTest {

    private static final AtomicLong SKUS = new AtomicLong();

    @Inject
    DualWriteCheckoutService dualWrite;

    @Inject
    CrashSwitch crashes;

    @BeforeEach
    void resetCrashSwitch() {
        crashes.reset();
    }

    @Test
    void crashAfterCommitLeavesOrderWithoutEvent() {
        long fulfillmentsBefore = Tx.call(() -> Fulfillment.count());
        CrashWindowException thrown = assertThrows(
                CrashWindowException.class,
                () -> dualWrite.place(request("lost-event"), CrashPoint.COMMIT));
        assertEquals(CrashPoint.COMMIT, thrown.point());

        PurchaseOrder order = latestOrder();
        assertNotNull(order);
        assertTrue(order.sku.startsWith("lost-event-"));
        assertEquals(0L, Tx.call(() -> OutboxEvent.count("aggregateid", Long.toString(order.id))));

        Awaitility.await()
                .pollInSameThread()
                .pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertEquals(fulfillmentsBefore, Tx.call(() -> Fulfillment.count()).longValue()));
        assertNull(Tx.call(() -> Fulfillment.findByOrderId(order.id)));
    }

    @Test
    void successfulDualWriteStillFulfills() {
        PurchaseOrder order = dualWrite.place(request("dual-ok"), CrashPoint.NONE);
        Awaitility.await()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(20))
                .until(() -> Tx.call(() -> Fulfillment.findByOrderId(order.id)) != null);
        assertEquals(0L, Tx.call(() -> OutboxEvent.count("aggregateid", Long.toString(order.id))));
    }

    private static CheckoutRequest request(String prefix) {
        return new CheckoutRequest(prefix + "-" + SKUS.incrementAndGet(), 1);
    }

    private static PurchaseOrder latestOrder() {
        return Tx.call(() -> PurchaseOrder.find("order by id desc").firstResult());
    }
}
