package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

@QuarkusTest
@TestProfile(CrashDisabledTest.Disabled.class)
class CrashDisabledTest {
    @Inject
    CheckoutService checkout;

    @Test
    void disabledCrashIsRejectedBeforeWriting() {
        long before = Tx.call(() -> PurchaseOrder.count());
        assertThrows(BadRequestException.class,
                () -> checkout.place(new CheckoutRequest("disabled-crash", 1), CrashPoint.COMMIT));
        assertEquals(before, Tx.call(() -> PurchaseOrder.count()).longValue());
    }

    public static class Disabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("checkout.crash.enabled", "false");
        }
    }
}
