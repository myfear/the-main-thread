package dev.themainthread.invoicerecon.mcp;

import dev.themainthread.invoicerecon.batch.ReconciliationBatchResult;

public final class ReconciliationResultJson {

    private ReconciliationResultJson() {
    }

    public static String toJson(ReconciliationBatchResult result) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"outcome\":\"").append(result.outcome()).append("\"");
        if (result.batchId() != null) {
            json.append(",\"batchId\":\"").append(result.batchId()).append("\"");
        }
        if (result.supplier() != null) {
            json.append(",\"supplier\":\"").append(result.supplier()).append("\"");
        }
        json.append(",\"processed\":").append(result.processed());
        json.append(",\"matched\":").append(result.matched());
        json.append(",\"exceptions\":").append(result.exceptions());
        if (result.status() != null) {
            json.append(",\"status\":\"").append(result.status()).append("\"");
        }
        if (result.message() != null) {
            json.append(",\"message\":\"").append(escape(result.message())).append("\"");
        }
        json.append('}');
        return json.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
