package com.themainthread.mavenlab;

import java.util.Map;

final class Json {
    private Json() {
    }

    static String encode(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            StringBuilder result = new StringBuilder("\"");
            for (char character : string.toCharArray()) {
                switch (character) {
                    case '"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    default -> {
                        if (character < 32) {
                            result.append(String.format("\\u%04x", (int) character));
                        } else {
                            result.append(character);
                        }
                    }
                }
            }
            return result.append('"').toString();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> encode(entry.getKey().toString()) + ": " + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",\n", "{\n", "\n}"));
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.StringJoiner values = new java.util.StringJoiner(", ", "[", "]");
            iterable.forEach(item -> values.add(encode(item)));
            return values.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
}
