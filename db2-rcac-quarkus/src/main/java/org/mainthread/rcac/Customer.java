package org.mainthread.rcac;

import java.math.BigDecimal;

public record Customer(long id, String tenantId, String displayName, String email,
        BigDecimal creditLimit, String status) {
}
