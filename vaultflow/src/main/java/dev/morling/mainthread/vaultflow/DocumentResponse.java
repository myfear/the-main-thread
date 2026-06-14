package dev.morling.mainthread.vaultflow;

public record DocumentResponse(
        Long id,
        String externalId,
        String ownerEmail,
        String title,
        String storageKey,
        String checksum) {
}
