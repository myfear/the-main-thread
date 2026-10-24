package dev.mainthread.pqc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PqcCliTest {
    @TempDir
    Path directory;

    private int run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            return PqcCli.run(args, stream, stream);
        }
    }

    @Test
    void serializedKeysRecoverOriginalBytes() throws Exception {
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        KeyFiles.generate(publicKey, privateKey);
        MessageCrypto crypto = new MessageCrypto();
        byte[] original = "Grüße, 東京".getBytes(StandardCharsets.UTF_8);
        var envelope = crypto.encrypt(KeyFiles.readPublic(publicKey), original);
        assertArrayEquals(original, crypto.decrypt(KeyFiles.readPrivate(privateKey), envelope));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ML-KEM-512", "ML-KEM-1024" })
    void importedKeysMustUse768(String algorithm) throws Exception {
        var pair = KeyPairGenerator.getInstance(algorithm).generateKeyPair();
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        Files.write(publicKey, pair.getPublic().getEncoded());
        Files.write(privateKey, pair.getPrivate().getEncoded());
        assertThrows(InvalidKeySpecException.class, () -> KeyFiles.readPublic(publicKey));
        assertThrows(InvalidKeySpecException.class, () -> KeyFiles.readPrivate(privateKey));
    }

    @Test
    void privateKeyAndRecoveredFileStartWithOwnerOnlyPermissions() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class));
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        assertTrue(KeyFiles.generate(publicKey, privateKey));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(privateKey));
        Path plaintext = directory.resolve("recovered.txt");
        LabFiles.writeNew(plaintext, new byte[0]);
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(plaintext));
    }

    @Test
    void keyGenerationDoesNotReplaceExistingPublicKeyOrLeavePrivateKey() throws Exception {
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        byte[] marker = new byte[] { 1, 2, 3 };
        Files.write(publicKey, marker);
        assertEquals(4, run("keygen", publicKey.toString(), privateKey.toString()));
        assertArrayEquals(marker, Files.readAllBytes(publicKey));
        assertFalse(Files.exists(privateKey));
    }

    @Test
    void existingPrivateKeyIsPreserved() throws Exception {
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        Files.writeString(privateKey, "preserve me");
        assertEquals(4, run("keygen", publicKey.toString(), privateKey.toString()));
        assertEquals("preserve me", Files.readString(privateKey));
        assertFalse(Files.exists(publicKey));
    }

    @Test
    void sameKeyPathsAreRejectedBeforeWriting() {
        Path path = directory.resolve("key.der");
        assertEquals(2, run("keygen", path.toString(), path.toString()));
        assertFalse(Files.exists(path));
    }

    @Test
    void danglingOutputSymlinkIsNeverFollowed() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class),
                "This filesystem test requires POSIX symbolic-link support.");
        Path target = directory.resolve("do-not-create.txt");
        Path link = directory.resolve("output.txt");
        Files.createSymbolicLink(link, target);
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> LabFiles.writeNew(link, new byte[] { 1 }));
        assertFalse(Files.exists(target));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    void boundedReadStopsAtLimitPlusOne() throws Exception {
        Path input = directory.resolve("input");
        Files.write(input, new byte[101]);
        assertThrows(IllegalArgumentException.class, () -> LabFiles.readBounded(input, 100));
        assertEquals(101, LabFiles.readBounded(input, 101).length);
    }

    @Test
    void malformedAndOversizedKeyFilesAreRejected() throws Exception {
        Path key = directory.resolve("key.der");
        Files.write(key, new byte[] { 1, 2, 3 });
        assertThrows(InvalidKeySpecException.class, () -> KeyFiles.readPublic(key));
        assertThrows(InvalidKeySpecException.class, () -> KeyFiles.readPrivate(key));
        Files.write(key, new byte[16 * 1024 + 1]);
        assertThrows(IllegalArgumentException.class, () -> KeyFiles.readPublic(key));
        assertThrows(IllegalArgumentException.class, () -> KeyFiles.readPrivate(key));
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(new String[0], new String[] { "unknown" }, new String[] { "keygen" },
                new String[] { "encrypt" }, new String[] { "decrypt" },
                new String[] { "keygen", "a", "b", "c" }).map(args -> Arguments.of((Object) args));
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void badUsageReturnsTwo(String[] args) {
        assertEquals(2, run(args));
    }

    @Test
    void helpSucceeds() {
        assertEquals(0, run("--help"));
    }
}
