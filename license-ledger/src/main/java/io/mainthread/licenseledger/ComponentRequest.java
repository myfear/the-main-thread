package io.mainthread.licenseledger;

public record ComponentRequest(
        String supplier,
        String groupId,
        String artifactId,
        String version,
        String licenseExpression) {
}
