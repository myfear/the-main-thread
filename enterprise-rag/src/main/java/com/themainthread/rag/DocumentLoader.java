package com.themainthread.rag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class DocumentLoader {
    private static final Logger LOG = Logger.getLogger(DocumentLoader.class);

    private final DoclingConverter converter;
    private final EmbeddingModel model;
    private final EmbeddingStore<TextSegment> store;
    private final RagConfig config;
    private final IndexingState state;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("rag-ingestion-", 0).factory());
    private volatile List<TextSegment> chunks = List.of();

    public DocumentLoader(DoclingConverter converter, EmbeddingModel model, EmbeddingStore<TextSegment> store,
            RagConfig config, IndexingState state) {
        this.converter = converter;
        this.model = model;
        this.store = store;
        this.config = config;
        this.state = state;
    }

    void start(@Observes StartupEvent event) {
        worker.execute(this::ingest);
    }

    void ingest() {
        int documents = 0;
        int written = 0;
        state.update(IndexingState.Status.INDEXING, 0, 0, "Converting and embedding the required PDF corpus");
        try {
            List<Path> files;
            try (var paths = Files.list(config.documentsDirectory())) {
                files = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".pdf"))
                        .sorted().toList();
            }
            if (files.isEmpty()) {
                state.update(IndexingState.Status.EMPTY, 0, 0, "No .pdf files in the configured documents directory");
                return;
            }
            var splitter = new DocumentBySentenceSplitter(config.chunkCharacters(), config.overlapCharacters());
            Files.createDirectories(config.convertedDirectory());
            // This application owns a dedicated demo table. Each startup replaces its entire index.
            store.removeAll();
            var allChunks = new java.util.ArrayList<TextSegment>();
            for (Path file : files) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Ingestion stopped");
                }
                String markdown = converter.convert(file).toCompletableFuture().get();
                Files.writeString(config.convertedDirectory().resolve(file.getFileName() + ".md"), markdown,
                        StandardCharsets.UTF_8);
                var segments = splitter.split(Document.from(markdown,
                        Metadata.from("file_name", file.getFileName().toString())));
                if (segments.isEmpty()) {
                    throw new IllegalStateException("No chunks for " + file.getFileName());
                }
                for (TextSegment segment : segments) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Ingestion stopped");
                    }
                    var embedding = model.embed(segment).content();
                    if (embedding.dimension() != config.embeddingDimension()) {
                        throw new IllegalStateException("Embedding width " + embedding.dimension()
                                + " does not match configured dimension " + config.embeddingDimension());
                    }
                    store.add(embedding, segment);
                    written++;
                }
                allChunks.addAll(segments);
                documents++;
                state.update(IndexingState.Status.INDEXING, documents, written, "Indexing the required corpus");
            }
            chunks = List.copyOf(allChunks);
            state.update(IndexingState.Status.READY, documents, written, "All required PDFs indexed");
            LOG.infof("RAG ready: %d PDFs, %d chunks", documents, written);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            state.update(IndexingState.Status.FAILED, documents, written, "Ingestion failed; inspect the application log");
            LOG.error("Required corpus ingestion failed", failure);
        }
    }

    public List<TextSegment> chunks() {
        return chunks;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
