package com.themainthread.rag;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;

@ApplicationScoped
public class DocumentRetriever implements RetrievalAugmentor {
    private final EmbeddingStoreContentRetriever retriever;
    private final RetrievalAugmentor delegate;

    public DocumentRetriever(EmbeddingStore<TextSegment> store, EmbeddingModel model, RagConfig config) {
        retriever = EmbeddingStoreContentRetriever.builder().embeddingStore(store).embeddingModel(model)
                .maxResults(3).minScore(config.minimumScore()).build();
        delegate = DefaultRetrievalAugmentor.builder().contentRetriever(retriever).build();
    }

    public List<Content> retrieve(String question) {
        return retriever.retrieve(Query.from(question));
    }

    @Override
    public AugmentationResult augment(AugmentationRequest request) {
        var result = delegate.augment(request);
        if (result.contents().isEmpty()) {
            throw new NoEvidenceException();
        }
        return result;
    }

    public static class NoEvidenceException extends RuntimeException {
    }
}
