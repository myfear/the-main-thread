package org.mainthread.rcac;

import java.util.Optional;

public enum PoolIdentity {
    TENANT_A("TENA", "A", false),
    TENANT_B("TENB", "B", false),
    TENANT_A_PII("TENAPII", "A", true);

    private final String databaseUser;
    private final String tenant;
    private final boolean sensitiveAccess;

    PoolIdentity(String databaseUser, String tenant, boolean sensitiveAccess) {
        this.databaseUser = databaseUser;
        this.tenant = tenant;
        this.sensitiveAccess = sensitiveAccess;
    }

    public static Optional<PoolIdentity> forPrincipal(String principal) {
        if (principal == null) {
            return Optional.empty();
        }
        return switch (principal) {
            case "alice" -> Optional.of(TENANT_A);
            case "bob" -> Optional.of(TENANT_B);
            case "auditor" -> Optional.of(TENANT_A_PII);
            default -> Optional.empty();
        };
    }

    public String databaseUser() {
        return databaseUser;
    }

    public String tenant() {
        return tenant;
    }

    public boolean sensitiveAccess() {
        return sensitiveAccess;
    }
}
