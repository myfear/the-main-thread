package com.themainthread.rag;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class NoEvidenceTest {
    @Test
    @SuppressWarnings("unchecked")
    void emptyIndexDoesNotCallGeneration() {
        var store = (EmbeddingStore<TextSegment>) mock(EmbeddingStore.class);
        var embedding = mock(EmbeddingModel.class);
        var chat = mock(ChatModel.class);
        var config = mock(RagConfig.class);
        when(config.minimumScore()).thenReturn(0.6);
        when(embedding.embed(any(String.class))).thenReturn(Response.from(Embedding.from(new float[384])));
        when(store.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of()));
        var retriever = new DocumentRetriever(store, embedding, config);
        var bot = AiServices.builder(SalesEnablementBot.class).chatModel(chat).retrievalAugmentor(retriever).build();
        assertThrows(DocumentRetriever.NoEvidenceException.class, () -> bot.answer("Show CloudX pricing"));
        verify(chat, never()).chat(any(ChatRequest.class));
    }
}
