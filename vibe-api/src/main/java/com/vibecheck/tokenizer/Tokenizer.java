package com.vibecheck.tokenizer;

public interface Tokenizer {
    long[] encode(String text, int maxLength);
}
