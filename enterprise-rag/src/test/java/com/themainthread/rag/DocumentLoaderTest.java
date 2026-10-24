package com.themainthread.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;

class DocumentLoaderTest {
    @TempDir
    Path directory;
    private DoclingConverter converter;
    private EmbeddingModel model;
    private EmbeddingStore<TextSegment> store;
    private RagConfig config;
    private IndexingState state;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        converter = mock(DoclingConverter.class);
        model = mock(EmbeddingModel.class);
        store = mock(EmbeddingStore.class);
        config = mock(RagConfig.class);
        state = new IndexingState();
        when(config.documentsDirectory()).thenReturn(directory);
        when(config.convertedDirectory()).thenReturn(directory.resolve("converted"));
        when(config.chunkCharacters()).thenReturn(1000);
        when(config.overlapCharacters()).thenReturn(150);
        when(config.embeddingDimension()).thenReturn(384);
        when(converter.convert(any())).thenReturn(CompletableFuture.completedFuture("# A required PDF\n\nEvidence."));
        when(model.embed(any(TextSegment.class))).thenReturn(Response.from(Embedding.from(new float[384])));
    }

    private void ingest() {
        var loader = new DocumentLoader(converter, model, store, config, state);
        try {
            loader.ingest();
        } finally {
            loader.stop();
        }
    }

    @Test
    void missingDirectoryFails() {
        when(config.documentsDirectory()).thenReturn(directory.resolve("missing"));
        ingest();
        assertEquals(IndexingState.Status.FAILED, state.snapshot().status());
        assertFalse(state.ready());
    }

    @Test
    void emptyCorpusIsNotReady() {
        ingest();
        assertEquals(IndexingState.Status.EMPTY, state.snapshot().status());
        assertFalse(state.ready());
        verify(store, never()).removeAll();
    }

    @Test
    void conversionFailureDoesNotBecomeReady() throws Exception {
        Files.writeString(directory.resolve("required.pdf"), "fixture");
        when(converter.convert(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("bad PDF")));
        ingest();
        assertEquals(IndexingState.Status.FAILED, state.snapshot().status());
        verify(store, never()).add(any(), any(TextSegment.class));
    }

    @Test
    void wrongEmbeddingWidthStopsBeforeWrite() throws Exception {
        Files.writeString(directory.resolve("required.pdf"), "fixture");
        when(model.embed(any(TextSegment.class))).thenReturn(Response.from(Embedding.from(new float[768])));
        ingest();
        assertEquals(IndexingState.Status.FAILED, state.snapshot().status());
        verify(store, never()).add(any(), any(TextSegment.class));
    }

    @Test
    void laterEmbeddingWriteFailureKeepsWholeCorpusUnavailable() throws Exception {
        Files.writeString(directory.resolve("first.pdf"), "fixture");
        Files.writeString(directory.resolve("second.pdf"), "fixture");
        when(store.add(any(), any(TextSegment.class))).thenReturn("id")
                .thenThrow(new IllegalStateException("write failed"));
        ingest();
        assertEquals(IndexingState.Status.FAILED, state.snapshot().status());
        assertEquals(1, state.snapshot().documents());
        assertFalse(state.ready());
    }

    @Test
    void completeCorpusBecomesReadyAndRetainsMetadata() throws Exception {
        Files.writeString(directory.resolve("required.pdf"), "fixture");
        ingest();
        assertTrue(state.ready());
        assertEquals(1, state.snapshot().documents());
        assertEquals(1, state.snapshot().chunks());
        assertTrue(Files.readString(directory.resolve("converted/required.pdf.md")).contains("Evidence"));
    }
}
