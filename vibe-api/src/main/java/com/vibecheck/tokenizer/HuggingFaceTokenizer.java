package com.vibecheck.tokenizer;

import java.io.IOException;
import java.util.Arrays;

import com.vibecheck.tokenizer.TokenizerType.Type;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@TokenizerType(Type.HUGGINGFACE)
public class HuggingFaceTokenizer implements Tokenizer {

    private final ai.djl.huggingface.tokenizers.HuggingFaceTokenizer tokenizer;
    private static final int PAD_TOKEN_ID = 1; // RoBERTa convention

    public HuggingFaceTokenizer() {
        try {
            this.tokenizer = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder()
                    .optTokenizerName("roberta-base")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tokenizer: roberta-base", e);
        }
    }

    public static HuggingFaceTokenizer newInstance(String modelId) {
        try {
            ai.djl.huggingface.tokenizers.HuggingFaceTokenizer tokenizer = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
                    .builder()
                    .optTokenizerName(modelId)
                    .build();
            return new HuggingFaceTokenizer(tokenizer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tokenizer: " + modelId, e);
        }
    }

    private HuggingFaceTokenizer(ai.djl.huggingface.tokenizers.HuggingFaceTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public long[] encode(String text, int maxLength) {
        ai.djl.huggingface.tokenizers.Encoding djlEncoding = tokenizer.encode(text);
        long[] ids = djlEncoding.getIds();

        if (ids.length > maxLength) {
            return Arrays.copyOf(ids, maxLength); // Truncate
        } else if (ids.length < maxLength) {
            long[] padded = Arrays.copyOf(ids, maxLength);
            Arrays.fill(padded, ids.length, maxLength, PAD_TOKEN_ID); // Pad
            return padded;
        }
        return ids;
    }

    public Encoding encode(String text) {
        ai.djl.huggingface.tokenizers.Encoding djlEncoding = tokenizer.encode(text);
        return new Encoding(djlEncoding);
    }

    public static class Encoding {
        private final ai.djl.huggingface.tokenizers.Encoding delegate;

        Encoding(ai.djl.huggingface.tokenizers.Encoding delegate) {
            this.delegate = delegate;
        }

        public long[] getIds() {
            return delegate.getIds();
        }

        public long[] getAttentionMask() {
            return delegate.getAttentionMask();
        }
    }
}
