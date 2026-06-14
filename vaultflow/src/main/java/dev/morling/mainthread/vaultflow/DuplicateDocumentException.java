package dev.morling.mainthread.vaultflow;

public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String externalId) {
        super("Document " + externalId + " already exists");
    }
}
