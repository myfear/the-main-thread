package dev.mainthread.pqc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.spec.GCMParameterSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageCryptoTest {
    private final MessageCrypto crypto = new MessageCrypto();

    static KeyPair recipient() throws Exception {
        return KeyPairGenerator.getInstance("ML-KEM-768").generateKeyPair();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "A contract", "Grüße, 東京, 🔐" })
    void roundTrip(String text) throws Exception {
        KeyPair pair = recipient();
        byte[] original = text.getBytes(StandardCharsets.UTF_8);
        MessageEnvelope envelope = crypto.encrypt(pair.getPublic(), original);
        assertEquals(1088, envelope.encapsulation().length);
        assertEquals(12, envelope.nonce().length);
        assertEquals(original.length + 16, envelope.ciphertext().length);
        assertArrayEquals(original, crypto.decrypt(pair.getPrivate(), MessageEnvelope.parse(envelope.encode())));
    }

    @Test
    void exactSizeLimitWorksAndLargerInputFails() throws Exception {
        KeyPair pair = recipient();
        byte[] original = new byte[MessageCrypto.MAX_PLAINTEXT_BYTES];
        Arrays.fill(original, (byte) 0xA5);
        MessageEnvelope envelope = crypto.encrypt(pair.getPublic(), original);
        assertArrayEquals(original, crypto.decrypt(pair.getPrivate(), MessageEnvelope.parse(envelope.encode())));
        assertThrows(IllegalArgumentException.class,
                () -> crypto.encrypt(pair.getPublic(), new byte[MessageCrypto.MAX_PLAINTEXT_BYTES + 1]));
    }

    @Test
    void wrongRecipientFails() throws Exception {
        var envelope = crypto.encrypt(recipient().getPublic(), new byte[] { 1, 2, 3 });
        assertThrows(AEADBadTagException.class, () -> crypto.decrypt(recipient().getPrivate(), envelope));
    }

    @ParameterizedTest
    @ValueSource(strings = { "encapsulation", "nonce", "ciphertext", "tag" })
    void modifiedBinaryFieldsFail(String field) throws Exception {
        KeyPair pair = recipient();
        MessageEnvelope original = crypto.encrypt(pair.getPublic(), new byte[] { 1, 2, 3 });
        byte[] encapsulation = original.encapsulation();
        byte[] nonce = original.nonce();
        byte[] ciphertext = original.ciphertext();
        switch (field) {
            case "encapsulation" -> encapsulation[0] ^= 1;
            case "nonce" -> nonce[0] ^= 1;
            case "ciphertext" -> ciphertext[0] ^= 1;
            case "tag" -> ciphertext[ciphertext.length - 1] ^= 1;
            default -> throw new AssertionError(field);
        }
        MessageEnvelope changed = new MessageEnvelope(encapsulation, nonce, ciphertext);
        assertThrows(AEADBadTagException.class, () -> crypto.decrypt(pair.getPrivate(), changed));
    }

    @Test
    void ciphertextFromDifferentAadIsRejected() throws Exception {
        KeyPair pair = recipient();
        var result = KEM.getInstance("ML-KEM-768").newEncapsulator(pair.getPublic()).encapsulate(0, 32, "AES");
        byte[] nonce = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, result.key(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD("different protocol context".getBytes(StandardCharsets.US_ASCII));
        MessageEnvelope envelope = new MessageEnvelope(result.encapsulation(), nonce, cipher.doFinal(new byte[] { 1 }));
        assertThrows(AEADBadTagException.class, () -> crypto.decrypt(pair.getPrivate(), envelope));
    }

    @Test
    void repeatedEncryptionUsesNewEncapsulationAndNonce() throws Exception {
        KeyPair pair = recipient();
        byte[] message = new byte[] { 1, 2, 3 };
        MessageEnvelope first = crypto.encrypt(pair.getPublic(), message);
        MessageEnvelope second = crypto.encrypt(pair.getPublic(), message);
        assertFalse(Arrays.equals(first.encapsulation(), second.encapsulation()));
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.encode(), second.encode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ML-KEM-512", "ML-KEM-1024" })
    void otherParameterSetsAreRejected(String algorithm) throws Exception {
        KeyPair wrong = KeyPairGenerator.getInstance(algorithm).generateKeyPair();
        assertThrows(InvalidKeyException.class, () -> crypto.encrypt(wrong.getPublic(), new byte[0]));
        MessageEnvelope envelope = crypto.encrypt(recipient().getPublic(), new byte[0]);
        assertThrows(InvalidKeyException.class, () -> crypto.decrypt(wrong.getPrivate(), envelope));
    }
}
