package io.mainthread.licenseledger;

public record ComponentReport(
        String supplier,
        String coordinate,
        String purl,
        String licenseExpression,
        String decision,
        String note) {
}
