package com.themainthread.pglite;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PostgresWire {
    private PostgresWire() {
    }

    record Frame(char type, ByteBuffer body) {
    }

    record Reply(QueryResult result, SqlException error) {
    }

    static byte[] startup() {
        byte[] parameters = "user\0postgres\0database\0template1\0client_encoding\0UTF8\0\0"
                .getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(8 + parameters.length).putInt(8 + parameters.length)
                .putInt(196608).put(parameters).array();
    }

    static byte[] query(String sql) {
        if (sql.isBlank() || sql.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SQL must be nonempty and contain no NUL characters");
        }
        byte[] text = sql.getBytes(StandardCharsets.UTF_8);
        if (text.length > 1024 * 1024) {
            throw new IllegalArgumentException("SQL exceeds the prototype's 1 MiB limit");
        }
        return message('Q', ByteBuffer.allocate(text.length + 1).put(text).put((byte) 0).array());
    }

    static byte[] password(byte[] salt) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String inner = HexFormat.of().formatHex(md5.digest("postgrespostgres".getBytes(StandardCharsets.UTF_8)));
            md5.update(inner.getBytes(StandardCharsets.US_ASCII));
            String response = "md5" + HexFormat.of().formatHex(md5.digest(salt)) + '\0';
            return message('p', response.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] message(char type, byte[] body) {
        return ByteBuffer.allocate(body.length + 5).put((byte) type).putInt(body.length + 4).put(body).array();
    }

    static List<Frame> frames(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        List<Frame> frames = new ArrayList<>();
        while (input.hasRemaining()) {
            if (input.remaining() < 5) {
                throw new IllegalStateException("Incomplete PostgreSQL frame header");
            }
            char type = (char) input.get();
            int length = input.getInt() - 4;
            if (length < 0 || length > input.remaining()) {
                throw new IllegalStateException("Invalid PostgreSQL frame length");
            }
            ByteBuffer body = input.slice().limit(length);
            input.position(input.position() + length);
            frames.add(new Frame(type, body));
            // Replies emitted after ReadyForQuery are stale re-emissions from the WASI wire pump.
            if (type == 'Z') {
                break;
            }
        }
        return frames;
    }

    static boolean complete(byte[] bytes, boolean authenticating) {
        for (Frame frame : frames(bytes)) {
            if (frame.type() == 'Z' || (authenticating && frame.type() == 'R' && frame.body().getInt(0) == 5)) {
                return true;
            }
        }
        return false;
    }

    static Reply decode(byte[] bytes) {
        List<QueryResult.StatementResult> results = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        SqlException error = null;
        String status = null;
        for (Frame frame : frames(bytes)) {
            ByteBuffer body = frame.body();
            switch (frame.type()) {
                case 'T' -> {
                    int count = Short.toUnsignedInt(body.getShort());
                    for (int i = 0; i < count; i++) {
                        columns.add(string(body));
                        body.position(body.position() + 18);
                    }
                }
                case 'D' -> {
                    int count = Short.toUnsignedInt(body.getShort());
                    List<String> row = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        int length = body.getInt();
                        if (length == -1) {
                            row.add(null);
                        } else {
                            byte[] value = new byte[length];
                            body.get(value);
                            row.add(new String(value, StandardCharsets.UTF_8));
                        }
                    }
                    rows.add(Collections.unmodifiableList(row));
                }
                case 'C' -> {
                    results.add(new QueryResult.StatementResult(List.copyOf(columns), List.copyOf(rows), string(body)));
                    columns.clear();
                    rows.clear();
                }
                case 'E' -> error = error(body);
                case 'Z' -> status = Character.toString((char) body.get());
                case 'I' -> results.add(new QueryResult.StatementResult(List.of(), List.of(), "EMPTY"));
                default -> {
                    // Notices, parameter status and backend keys do not carry query rows.
                }
            }
        }
        if (status == null || !List.of("I", "T", "E").contains(status)) {
            throw new IllegalStateException("Missing or invalid ReadyForQuery transaction status");
        }
        return new Reply(new QueryResult(List.copyOf(results), status), error);
    }

    static SqlException error(ByteBuffer body) {
        Map<Character, String> fields = new LinkedHashMap<>();
        while (body.hasRemaining()) {
            char code = (char) body.get();
            if (code == 0) {
                break;
            }
            fields.put(code, string(body));
        }
        return new SqlException(fields.getOrDefault('C', "XXXXX"), fields.getOrDefault('M', "PostgreSQL error"));
    }

    private static String string(ByteBuffer body) {
        int start = body.position();
        while (body.hasRemaining()) {
            if (body.get() == 0) {
                int end = body.position() - 1;
                byte[] text = new byte[end - start];
                body.duplicate().position(start).get(text);
                return new String(text, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Unterminated PostgreSQL string");
    }
}
