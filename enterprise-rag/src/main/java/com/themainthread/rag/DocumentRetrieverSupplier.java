package com.themainthread.rag;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.rag.RetrievalAugmentor;

@ApplicationScoped
public class DocumentRetrieverSupplier implements Supplier<RetrievalAugmentor> {
    private final DocumentRetriever retriever;

    public DocumentRetrieverSupplier(DocumentRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public RetrievalAugmentor get() {
        return retriever;
    }
}
