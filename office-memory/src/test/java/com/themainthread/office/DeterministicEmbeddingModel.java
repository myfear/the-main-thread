package com.themainthread.office;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

@Alternative
@Priority(1)
@Singleton
public class DeterministicEmbeddingModel implements EmbeddingModel {
    public boolean fail;
    public int dimensions = 768;
    public final List<String> inputs = new CopyOnWriteArrayList<>();
    private final TransactionManager transactions;

    public DeterministicEmbeddingModel(TransactionManager transactions) {
        this.transactions = transactions;
    }

    @Override
    public Response<Embedding> embed(String text) {
        try {
            if (transactions.getStatus() != Status.STATUS_NO_TRANSACTION) {
                throw new AssertionError("Embedding I/O must not hold a database transaction");
            }
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
        inputs.add(text);
        if (fail) {
            throw new IllegalStateException("Simulated embedding failure");
        }
        float[] vector = new float[dimensions];
        // Identical vectors deliberately make every row an equally close match.
        // Isolation must therefore happen in the database before the result limit.
        vector[0] = 1;
        return Response.from(Embedding.from(vector));
    }

    public void reset() {
        fail = false;
        dimensions = 768;
        inputs.clear();
    }
}
