package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PoolIdentityTest {
    @Test
    void routesOnlyExactAuthenticatedPrincipalNames() {
        assertEquals(PoolIdentity.TENANT_A, PoolIdentity.forPrincipal("alice").orElseThrow());
        assertEquals(PoolIdentity.TENANT_B, PoolIdentity.forPrincipal("bob").orElseThrow());
        assertEquals(PoolIdentity.TENANT_A_PII, PoolIdentity.forPrincipal("auditor").orElseThrow());
        for (String unknown : new String[] { "", "ALICE", "tenant-a", "TENA", "alice ", "unmapped", "admin" }) {
            assertTrue(PoolIdentity.forPrincipal(unknown).isEmpty(), unknown);
        }
        assertTrue(PoolIdentity.forPrincipal(null).isEmpty());
    }
}
