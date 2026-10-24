package com.themainthread.rag;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IndexingState {
    public enum Status { INDEXING, READY, EMPTY, FAILED }

    public record Snapshot(Status status, int documents, int chunks, String detail) {
    }

    private volatile Snapshot snapshot = new Snapshot(Status.INDEXING, 0, 0, "Indexing has not completed");

    public Snapshot snapshot() {
        return snapshot;
    }

    void update(Status status, int documents, int chunks, String detail) {
        snapshot = new Snapshot(status, documents, chunks, detail);
    }

    public boolean ready() {
        return snapshot.status() == Status.READY;
    }
}
