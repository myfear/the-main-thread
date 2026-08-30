package com.themainthread.carrierwebhooks.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.themainthread.carrierwebhooks.api.WebhookProblem;
import com.themainthread.carrierwebhooks.config.WebhookConfiguration;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebhookSignatureVerifier {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final WebhookConfiguration configuration;

    public WebhookSignatureVerifier(WebhookConfiguration configuration) {
        this.configuration = configuration;
    }

    public void verify(String signature, String payload) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new WebhookProblem(401, "invalid_signature", "A sha256 webhook signature is required");
        }

        byte[] expected = hmac(payload);
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature.substring("sha256=".length()));
        } catch (IllegalArgumentException exception) {
            throw new WebhookProblem(401, "invalid_signature", "The webhook signature is not hexadecimal");
        }

        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new WebhookProblem(401, "invalid_signature", "The webhook signature does not match the payload");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(configuration.sharedSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException exception) {
            throw new IllegalStateException("The configured webhook secret is invalid", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM does not provide HmacSHA256", exception);
        }
    }
}
