package dev.mainthread.pqc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MessageEnvelopeTest {
    private static MessageEnvelope example() {
        return new MessageEnvelope(new byte[1088], new byte[12], new byte[16]);
    }

    static Stream<String> malformed() {
        String valid = new String(example().encode(), StandardCharsets.US_ASCII);
        return Stream.of(
                valid.replace("PQC-DEMO-1", "PQC-DEMO-2"),
                valid.replace("ML-KEM-768", "ML-KEM-512"),
                valid.replace("AES-256-GCM", "AES-128-GCM"),
                valid + "extra=value\n",
                valid.replace("nonce=", "encapsulation="),
                valid.replace("ciphertext=", "nonce="),
                valid.replace("nonce=AAAAAAAAAAAAAAAA\n", ""),
                valid.replace("nonce=AAAAAAAAAAAAAAAA", "nonce=%%%"),
                valid.replace("nonce=AAAAAAAAAAAAAAAA", "nonce=AA=="),
                valid.replace("ciphertext=AAAAAAAAAAAAAAAAAAAAAA==", "ciphertext=AA=="),
                valid.replace("encapsulation=", "encapsulation=AA=="),
                valid.replace("==\n", "\n"),
                valid.replace("\n", "\r\n"),
                valid.replace("cipher=AES", "cipher= AES"),
                valid.substring(0, valid.length() - 1),
                valid + "\n",
                "",
                "☃\n");
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void rejectsMalformedEnvelope(String encoded) {
        assertThrows(IllegalArgumentException.class,
                () -> MessageEnvelope.parse(encoded.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void capsTotalAndDecodedSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageEnvelope.parse(new byte[MessageEnvelope.MAX_ENVELOPE_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> new MessageEnvelope(new byte[1088], new byte[12],
                        new byte[MessageCrypto.MAX_PLAINTEXT_BYTES + 17]));
        assertThrows(IllegalArgumentException.class,
                () -> new MessageEnvelope(new byte[1087], new byte[12], new byte[16]));
    }

    @Test
    void ownsItsArrays() {
        byte[] encapsulation = new byte[1088];
        byte[] nonce = new byte[12];
        byte[] ciphertext = new byte[16];
        MessageEnvelope envelope = new MessageEnvelope(encapsulation, nonce, ciphertext);
        encapsulation[0] = 1;
        nonce[0] = 1;
        ciphertext[0] = 1;
        envelope.encapsulation()[0] = 2;
        envelope.nonce()[0] = 2;
        envelope.ciphertext()[0] = 2;
        assertArrayEquals(new byte[1088], envelope.encapsulation());
        assertArrayEquals(new byte[12], envelope.nonce());
        assertArrayEquals(new byte[16], envelope.ciphertext());
    }
}
