package dev.mainthread.pqc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

record MessageEnvelope(byte[] encapsulation, byte[] nonce, byte[] ciphertext) {
    static final int MAX_ENVELOPE_BYTES = 2 * 1024 * 1024;
    private static final String HEADER = "format=PQC-DEMO-1\nkem=ML-KEM-768\ncipher=AES-256-GCM\n";

    MessageEnvelope {
        if (encapsulation.length != MessageCrypto.ENCAPSULATION_BYTES
                || nonce.length != MessageCrypto.NONCE_BYTES
                || ciphertext.length < MessageCrypto.TAG_BYTES
                || ciphertext.length > MessageCrypto.MAX_PLAINTEXT_BYTES + MessageCrypto.TAG_BYTES) {
            throw new IllegalArgumentException("Invalid envelope field length.");
        }
        encapsulation = encapsulation.clone();
        nonce = nonce.clone();
        ciphertext = ciphertext.clone();
    }

    @Override
    public byte[] encapsulation() {
        return encapsulation.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    static byte[] authenticatedHeader() {
        return HEADER.getBytes(StandardCharsets.US_ASCII);
    }

    byte[] encode() {
        Base64.Encoder encoder = Base64.getEncoder();
        String text = HEADER
                + "encapsulation=" + encoder.encodeToString(encapsulation) + "\n"
                + "nonce=" + encoder.encodeToString(nonce) + "\n"
                + "ciphertext=" + encoder.encodeToString(ciphertext) + "\n";
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    static MessageEnvelope parse(byte[] encoded) {
        if (encoded.length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Envelope exceeds the 2 MiB lab limit.");
        }
        for (byte value : encoded) {
            if (value < 0 || value == '\r') {
                throw new IllegalArgumentException("Envelope requires ASCII and LF line endings.");
            }
        }
        String text = new String(encoded, StandardCharsets.US_ASCII);
        String[] lines = text.split("\n", -1);
        if (!text.startsWith(HEADER) || lines.length != 7 || !lines[6].isEmpty()) {
            throw new IllegalArgumentException("Unsupported or malformed envelope header/fields.");
        }
        return new MessageEnvelope(decode(lines[3], "encapsulation="),
                decode(lines[4], "nonce="), decode(lines[5], "ciphertext="));
    }

    private static byte[] decode(String line, String prefix) {
        if (!line.startsWith(prefix)) {
            throw new IllegalArgumentException("Missing or reordered envelope field.");
        }
        String value = line.substring(prefix.length());
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid envelope Base64.");
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("Envelope Base64 must use canonical padding.");
        }
        return decoded;
    }
}
