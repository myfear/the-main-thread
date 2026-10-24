package dev.mainthread.pqc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PqcCliIT {
    @TempDir
    Path directory;

    private record Result(int status, String output) {
    }

    private Result process(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(System.getProperty("demo.jar"));
        command.addAll(List.of(arguments));
        Path log = Files.createTempFile(directory, "process-", ".log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!child.waitFor(20, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("CLI did not exit within 20 seconds.");
        }
        return new Result(child.exitValue(), Files.readString(log));
    }

    private Path path(String name) {
        return directory.resolve(name);
    }

    private void prepare(byte[] original) throws Exception {
        assertEquals(0, process("keygen", path("public.der").toString(), path("private.der").toString()).status());
        Files.write(path("input.txt"), original);
        assertEquals(0, process("encrypt", path("public.der").toString(), path("input.txt").toString(),
                path("contract.pqc").toString()).status());
    }

    private Result decrypt(Path privateKey) throws Exception {
        return process("decrypt", privateKey.toString(), path("contract.pqc").toString(), path("recovered.txt").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "Contract: Grüße, 東京, 🔐" })
    void packagedJarWorksAcrossThreeSeparateJvms(String text) throws Exception {
        byte[] original = text.getBytes(StandardCharsets.UTF_8);
        prepare(original);
        Result result = decrypt(path("private.der"));
        assertEquals(0, result.status(), result.output());
        assertTrue(result.output().contains("authentication passed"));
        assertArrayEquals(original, Files.readAllBytes(path("recovered.txt")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "encapsulation", "nonce", "ciphertext", "header", "base64", "duplicate" })
    void modifiedEnvelopeExitsNonzeroWithoutOutput(String mutation) throws Exception {
        prepare("Confidential contract".getBytes(StandardCharsets.UTF_8));
        byte[] bytes = Files.readAllBytes(path("contract.pqc"));
        if (List.of("encapsulation", "nonce", "ciphertext").contains(mutation)) {
            MessageEnvelope original = MessageEnvelope.parse(bytes);
            byte[] encapsulation = original.encapsulation();
            byte[] nonce = original.nonce();
            byte[] ciphertext = original.ciphertext();
            switch (mutation) {
                case "encapsulation" -> encapsulation[0] ^= 1;
                case "nonce" -> nonce[0] ^= 1;
                case "ciphertext" -> ciphertext[0] ^= 1;
                default -> throw new AssertionError(mutation);
            }
            Files.write(path("contract.pqc"), new MessageEnvelope(encapsulation, nonce, ciphertext).encode());
        } else {
            String encoded = new String(bytes, StandardCharsets.US_ASCII);
            String changed = switch (mutation) {
                case "header" -> encoded.replace("PQC-DEMO-1", "PQC-DEMO-2");
                case "base64" -> encoded.replace("nonce=", "nonce=%");
                case "duplicate" -> encoded + "nonce=AAAAAAAAAAAAAAAA\n";
                default -> throw new AssertionError(mutation);
            };
            Files.writeString(path("contract.pqc"), changed);
        }
        Result result = decrypt(path("private.der"));
        int expected = List.of("encapsulation", "nonce", "ciphertext").contains(mutation) ? 3 : 2;
        assertEquals(expected, result.status(), result.output());
        assertFalse(Files.exists(path("recovered.txt")));
        assertFalse(result.output().contains("Exception"));
        assertFalse(result.output().contains("Confidential contract"));
    }

    @Test
    void wrongKeyDoesNotCreatePlaintext() throws Exception {
        prepare(new byte[] { 1, 2, 3 });
        assertEquals(0, process("keygen", path("other-public.der").toString(), path("other-private.der").toString()).status());
        Result result = decrypt(path("other-private.der"));
        assertEquals(3, result.status(), result.output());
        assertEquals("Decryption failed: wrong key or modified message.\n", result.output());
        assertFalse(Files.exists(path("recovered.txt")));
    }

    @Test
    void existingPlaintextIsPreservedOnSuccessAndAuthenticationFailure() throws Exception {
        prepare(new byte[] { 1 });
        Files.writeString(path("recovered.txt"), "keep this");
        assertEquals(4, decrypt(path("private.der")).status());
        byte[] changed = Files.readAllBytes(path("contract.pqc"));
        String encoded = new String(changed, StandardCharsets.US_ASCII);
        int start = encoded.indexOf("ciphertext=") + "ciphertext=".length();
        changed[start] = changed[start] == 'A' ? (byte) 'B' : (byte) 'A';
        Files.write(path("contract.pqc"), changed);
        assertEquals(3, decrypt(path("private.der")).status());
        assertEquals("keep this", Files.readString(path("recovered.txt")));
    }

    @Test
    void oversizedInputsAndExistingEnvelopeFailWithoutReplacement() throws Exception {
        prepare(new byte[] { 1 });
        byte[] originalEnvelope = Files.readAllBytes(path("contract.pqc"));
        assertEquals(4, process("encrypt", path("public.der").toString(), path("input.txt").toString(),
                path("contract.pqc").toString()).status());
        assertArrayEquals(originalEnvelope, Files.readAllBytes(path("contract.pqc")));
        Files.write(path("input.txt"), new byte[MessageCrypto.MAX_PLAINTEXT_BYTES + 1]);
        assertEquals(2, process("encrypt", path("public.der").toString(), path("input.txt").toString(),
                path("oversized.pqc").toString()).status());
        assertFalse(Files.exists(path("oversized.pqc")));
        Files.write(path("contract.pqc"), new byte[MessageEnvelope.MAX_ENVELOPE_BYTES + 1]);
        assertEquals(2, decrypt(path("private.der")).status());
        assertFalse(Files.exists(path("recovered.txt")));
    }

    @Test
    void missingFilesAndWrongArgumentsReturnNonzero() throws Exception {
        assertEquals(2, process("decrypt").status());
        assertEquals(4, process("encrypt", path("missing.der").toString(), path("missing.txt").toString(),
                path("never.pqc").toString()).status());
        assertFalse(Files.exists(path("never.pqc")));
    }
}
