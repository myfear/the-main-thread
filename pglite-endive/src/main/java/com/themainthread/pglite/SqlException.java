package com.themainthread.pglite;

/** A PostgreSQL ErrorResponse. The database instance remains usable. */
public final class SqlException extends RuntimeException {
    private final String sqlState;

    SqlException(String sqlState, String message) {
        super(message);
        this.sqlState = sqlState;
    }

    public String sqlState() {
        return sqlState;
    }
}
