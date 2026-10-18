package com.themainthread.codemode;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class Json {
    static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private Json() {
    }

    static JsonNode parse(String value) {
        try {
            JsonNode node = MAPPER.readTree(value);
            if (node == null) {
                throw new IOException("Empty JSON");
            }
            return node;
        } catch (IOException e) {
            throw new CodeModeFailure("INVALID_JSON", "Expected one JSON value.");
        }
    }

    static byte[] bytes(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }
}
