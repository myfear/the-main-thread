package dev.mainthread.pqc;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import javax.crypto.AEADBadTagException;
import javax.crypto.DecapsulateException;

public final class PqcCli {
    private PqcCli() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("--help")) {
            usage(out);
            return args.length == 0 ? 2 : 0;
        }
        try {
            MessageCrypto crypto = new MessageCrypto();
            switch (args[0]) {
                case "keygen" -> {
                    requireArguments(args, 3);
                    boolean posix = KeyFiles.generate(Path.of(args[1]), Path.of(args[2]));
                    out.println("Created recipient keys (ML-KEM-768).");
                    out.println("Private key is unencrypted lab data; keep it local and out of version control.");
                    if (!posix) {
                        err.println("POSIX permissions unavailable; secure the private key using your filesystem's access controls.");
                    }
                }
                case "encrypt" -> {
                    requireArguments(args, 4);
                    var key = KeyFiles.readPublic(Path.of(args[1]));
                    byte[] plaintext = LabFiles.readBounded(Path.of(args[2]), MessageCrypto.MAX_PLAINTEXT_BYTES);
                    MessageEnvelope envelope = crypto.encrypt(key, plaintext);
                    LabFiles.writeNew(Path.of(args[3]), envelope.encode());
                    out.println("Encrypted " + plaintext.length + " bytes (ML-KEM-768 + AES-256-GCM).");
                }
                case "decrypt" -> {
                    requireArguments(args, 4);
                    var key = KeyFiles.readPrivate(Path.of(args[1]));
                    MessageEnvelope envelope = MessageEnvelope.parse(
                            LabFiles.readBounded(Path.of(args[2]), MessageEnvelope.MAX_ENVELOPE_BYTES));
                    byte[] plaintext = crypto.decrypt(key, envelope);
                    LabFiles.writeNew(Path.of(args[3]), plaintext);
                    out.println("Decrypted " + plaintext.length + " bytes; authentication passed.");
                }
                default -> throw new IllegalArgumentException("Unknown command; use --help.");
            }
            return 0;
        } catch (AEADBadTagException | DecapsulateException e) {
            err.println("Decryption failed: wrong key or modified message.");
            return 3;
        } catch (FileAlreadyExistsException e) {
            err.println("Output already exists; choose a new path.");
            return 4;
        } catch (IllegalArgumentException e) {
            err.println("Invalid input: " + e.getMessage());
            return 2;
        } catch (GeneralSecurityException e) {
            err.println("Cryptographic operation failed; check the key encoding and ML-KEM-768 parameter set.");
            return 4;
        } catch (IOException e) {
            err.println("File operation failed; check paths, permissions, and existing destinations.");
            return 4;
        }
    }

    private static void requireArguments(String[] args, int count) {
        if (args.length != count) {
            throw new IllegalArgumentException("Incorrect argument count; use --help.");
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage: java -jar target/pqc-demo.jar <command> <paths>");
        out.println("  keygen <public.der> <private.der>");
        out.println("  encrypt <public.der> <input> <envelope.pqc>");
        out.println("  decrypt <private.der> <envelope.pqc> <output>");
        out.println("Small-file teaching format. Private keys are unencrypted; existing outputs are never replaced.");
    }
}
