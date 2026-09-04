package com.themainthread.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.aesh.AeshLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
class TerminalCommandTest {

    @Test
    void operatesAHubThroughTheRepl(AeshLauncher launcher) {
        launcher.launch();
        try {
            String status = launcher.executeCommand("status");
            assertTrue(status.contains("berlin"));
            assertTrue(status.contains("DEGRADED"));

            String dryRun = launcher.executeCommand("hub retry --hub=berlin SHP-1042 --dry-run");
            assertTrue(dryRun.contains("Would retry SHP-1042 through fallback-dhl"), dryRun);

            String retry = launcher.executeCommand("hub retry --hub=berlin SHP-1042 --confirm=SHP-1042");
            assertTrue(retry.contains("Shipment SHP-1042 queued through fallback-dhl"), retry);

            String repeated = launcher.executeCommand("hub retry --hub=berlin SHP-1042 --confirm=SHP-1042");
            assertTrue(repeated.contains("already queued for retry"), repeated);

            String audit = launcher.executeCommand("audit --limit=3");
            assertTrue(audit.contains("retry SHP-1042 in berlin"), audit);
        } finally {
            launcher.exit();
        }
    }
}
