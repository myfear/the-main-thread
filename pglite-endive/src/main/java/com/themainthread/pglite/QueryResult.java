package com.themainthread.pglite;

import java.util.List;

/** PostgreSQL text-format results; SQL NULL is represented by Java null. */
public record QueryResult(List<StatementResult> statements, String transactionStatus) {
    public record StatementResult(List<String> columns, List<List<String>> rows, String command) {
    }
}
