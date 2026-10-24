package dev.mainthread.pqc;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class MessageCrypto {
    static final String KEM_ALGORITHM = "ML-KEM-768";
    static final int MAX_PLAINTEXT_BYTES = 1024 * 1024;
    static final int ENCAPSULATION_BYTES = 1088;
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;

    MessageEnvelope encrypt(PublicKey recipient, byte[] plaintext) throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("Plaintext exceeds the 1 MiB lab limit.");
        }
        KEM kem = KEM.getInstance(KEM_ALGORITHM);
        KEM.Encapsulated result = kem.newEncapsulator(recipient).encapsulate(0, 32, "AES");
        byte[] nonce = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, result.key(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(MessageEnvelope.authenticatedHeader());
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new MessageEnvelope(result.encapsulation(), nonce, ciphertext);
    }

    byte[] decrypt(PrivateKey recipient, MessageEnvelope envelope) throws GeneralSecurityException {
        KEM kem = KEM.getInstance(KEM_ALGORITHM);
        SecretKey key = kem.newDecapsulator(recipient)
                .decapsulate(envelope.encapsulation(), 0, 32, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope.nonce()));
        cipher.updateAAD(MessageEnvelope.authenticatedHeader());
        return cipher.doFinal(envelope.ciphertext());
    }
}
