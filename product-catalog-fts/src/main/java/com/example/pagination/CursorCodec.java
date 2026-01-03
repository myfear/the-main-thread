package com.example.pagination;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CursorCodec {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final ObjectMapper mapper;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CursorCodec(ObjectMapper mapper,
            @ConfigProperty(name = "catalog.cursor.key-b64") String keyB64) {
        this.mapper = mapper;
        byte[] raw = Base64.getDecoder().decode(keyB64);
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encode(CursorPayload payload) {
        try {
            byte[] json = mapper.writeValueAsBytes(payload);

            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(json);

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public CursorPayload decode(String token) {
        try {
            byte[] in = Base64.getUrlDecoder().decode(token);

            if (in.length < IV_BYTES + 1) {
                throw new IllegalArgumentException("Cursor is too short");
            }

            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[in.length - IV_BYTES];
            System.arraycopy(in, 0, iv, 0, IV_BYTES);
            System.arraycopy(in, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            byte[] json = cipher.doFinal(ciphertext);
            return mapper.readValue(json, CursorPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }
}