package dev.morling.mainthread.vaultflow;

public record CreateDocumentRequest(
        String externalId,
        String ownerEmail,
        String title,
        String storageKey,
        String checksum) {
}
