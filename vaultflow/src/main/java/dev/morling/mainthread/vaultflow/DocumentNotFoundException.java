package dev.morling.mainthread.vaultflow;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String externalId) {
        super("Document " + externalId + " was not found");
    }
}
