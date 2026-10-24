package dev.mainthread.pqc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

final class KeyFiles {
    private static final int MAX_KEY_BYTES = 16 * 1024;

    private KeyFiles() {
    }

    static boolean generate(Path publicPath, Path privatePath) throws GeneralSecurityException, IOException {
        if (publicPath.toAbsolutePath().normalize().equals(privatePath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Public and private key paths must differ.");
        }
        KeyPair pair = KeyPairGenerator.getInstance(MessageCrypto.KEM_ALGORITHM).generateKeyPair();
        byte[] encodedPrivate = pair.getPrivate().getEncoded();
        boolean posix;
        try {
            posix = LabFiles.writeNew(privatePath, encodedPrivate);
        } finally {
            Arrays.fill(encodedPrivate, (byte) 0);
        }
        try {
            LabFiles.writeNew(publicPath, pair.getPublic().getEncoded());
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(privatePath);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        return posix;
    }

    static PublicKey readPublic(Path path) throws GeneralSecurityException, IOException {
        return KeyFactory.getInstance(MessageCrypto.KEM_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(LabFiles.readBounded(path, MAX_KEY_BYTES)));
    }

    static PrivateKey readPrivate(Path path) throws GeneralSecurityException, IOException {
        byte[] bytes = LabFiles.readBounded(path, MAX_KEY_BYTES);
        try {
            return KeyFactory.getInstance(MessageCrypto.KEM_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
