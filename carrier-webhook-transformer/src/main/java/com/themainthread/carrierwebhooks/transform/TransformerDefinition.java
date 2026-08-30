package com.themainthread.carrierwebhooks.transform;

public record TransformerDefinition(String carrier, String version, String source, String sha256) {
}
