package com.themainthread.terminal.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DispatchServiceTest {

    @Inject
    DispatchService dispatchService;

    @BeforeEach
    void resetState() {
        dispatchService.reset();
    }

    @Test
    void requiresConfirmationAndKeepsRetryIdempotent() {
        var refused = dispatchService.retry("berlin", "SHP-1042", false, "wrong");
        assertFalse(refused.successful());
        assertEquals(2, dispatchService.hub("berlin").orElseThrow().failed());

        var dryRun = dispatchService.retry("berlin", "SHP-1042", true, null);
        assertTrue(dryRun.successful());
        assertEquals(2, dispatchService.hub("berlin").orElseThrow().failed());

        var queued = dispatchService.retry("berlin", "SHP-1042", false, "SHP-1042");
        assertTrue(queued.successful());
        assertEquals(1, dispatchService.hub("berlin").orElseThrow().failed());

        var repeated = dispatchService.retry("berlin", "SHP-1042", false, "SHP-1042");
        assertTrue(repeated.successful());
        assertTrue(repeated.message().contains("already queued"));
        assertEquals(185, dispatchService.hub("berlin").orElseThrow().queued());
    }
}
