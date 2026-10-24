# Post-quantum first message

Encrypt a small file for a recipient using Java 25's ML-KEM-768 implementation and AES-256-GCM. Generate keys, encrypt, and decrypt in separate processes. The application has no runtime dependencies.

Article: [The Key Is the Problem: Post-Quantum Cryptography in Java 25](https://www.the-main-thread.com/p/post-quantum-cryptography-java-25).

This is an educational file format, with a 1 MiB plaintext limit. It provides no sender identity, recipient public-key authentication, or forward secrecy. **The lab writes an unencrypted private key. Use fictional data, keep generated files local, and never commit the key.** Use established protocols and supported key storage for production systems.

## Build and test

Use JDK 25 and an internet connection for the first Maven Wrapper build. Run commands from this directory. Verification examples use a POSIX shell, `awk`, and `cmp`.

```bash
./mvnw clean verify
```

The wrapper pins Maven 3.9.16. The build targets Java 25 and uses JUnit Jupiter 6.1.3, Compiler Plugin 3.16.0, Surefire/Failsafe 3.6.0, Jar Plugin 3.5.1, and Exec Plugin 3.6.4. Windows users can build with `mvnw.cmd clean verify` and run the same `java -jar` commands.

Verified with Temurin 25.0.4.1 and the wrapper's Maven 3.9.16:

```text
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The first total covers unit tests. The second covers the packaged JAR in child JVMs, including failure exit statuses and output-file behavior. `./mvnw test` runs only the unit tests; use `verify` for both groups.

## Encrypt and recover the sample

Start with unused output paths. The CLI creates parent directories and refuses to replace existing files, including symbolic links. On POSIX filesystems it creates all output files with mode `0600`. On other filesystems, key generation prints a warning; configure your filesystem's access controls before using the private key.

```bash
java -jar target/pqc-demo.jar keygen target/demo/recipient-public.der target/demo/recipient-private.der
java -jar target/pqc-demo.jar encrypt target/demo/recipient-public.der samples/contract.txt target/demo/contract.pqc
java -jar target/pqc-demo.jar decrypt target/demo/recipient-private.der target/demo/contract.pqc target/demo/recovered.txt
cmp samples/contract.txt target/demo/recovered.txt
```

Observed CLI output:

```text
Created recipient keys (ML-KEM-768).
Private key is unencrypted lab data; keep it local and out of version control.
Encrypted 227 bytes (ML-KEM-768 + AES-256-GCM).
Decrypted 227 bytes; authentication passed.
```

`cmp` prints nothing and exits with status 0 when the bytes match. The sample contains fictional contract text with non-ASCII characters. The application processes bytes and also supports empty files and binary data within the size limit.

Only the public key goes to the sender. This local lab assumes you provide the genuine recipient key. Anyone with that public key can create an encrypted message; successful decryption does not identify its sender.

## Inspect and corrupt an envelope

Display the fixed metadata and all six field names without printing long Base64 values:

```bash
head -n 3 target/demo/contract.pqc
cut -d= -f1 target/demo/contract.pqc
```

Change the first Base64 character of the nonce, keeping the field well formed:

```bash
awk '/^nonce=/ {
    first = substr($0, 7, 1)
    $0 = "nonce=" (first == "A" ? "B" : "A") substr($0, 8)
} { print }' target/demo/contract.pqc > target/demo/tampered.pqc

java -jar target/pqc-demo.jar decrypt target/demo/recipient-private.der target/demo/tampered.pqc target/demo/rejected.txt
echo $?
test ! -e target/demo/rejected.txt
```

Observed output from the decryption command and `echo`:

```text
Decryption failed: wrong key or modified message.
3
```

The final `test` exits 0 and prints nothing. No recovered output was created. If your shell stops automatically on a nonzero command, run the failure demonstration interactively or explicitly handle that status.

For a wrong-key failure, generate a second recipient and try its private key:

```bash
java -jar target/pqc-demo.jar keygen target/demo/other-public.der target/demo/other-private.der
java -jar target/pqc-demo.jar decrypt target/demo/other-private.der target/demo/contract.pqc target/demo/wrong-key.txt
echo $?
test ! -e target/demo/wrong-key.txt
```

The decryption command produces the same failure message and exit code 3. Existing destination files are preserved on both authentication and overwrite failures.

## Run through Maven

After building, the following also work as three independent Maven invocations. They use a separate set of output paths:

```bash
./mvnw -q exec:java -Dexec.args="keygen target/maven-demo/recipient-public.der target/maven-demo/recipient-private.der"
./mvnw -q exec:java -Dexec.args="encrypt target/maven-demo/recipient-public.der samples/contract.txt target/maven-demo/contract.pqc"
./mvnw -q exec:java -Dexec.args="decrypt target/maven-demo/recipient-private.der target/maven-demo/contract.pqc target/maven-demo/recovered.txt"
cmp samples/contract.txt target/maven-demo/recovered.txt
```

These invocations produced the same four CLI output lines shown for the JAR workflow, and the comparison exited 0. Each `exec:java` runs inside that invocation's Maven JVM. The plugin traps `System.exit` so a CLI failure fails the Maven goal: the verified tampering case returned Maven status **1**, while a direct JAR run returned status **3**.

## Format and limits

`PQC-DEMO-1` is an ASCII format with exactly six ordered lines and a final LF newline. CRLF, extra fields, duplicates, reordered fields, unsupported identifiers, and noncanonical Base64 are rejected.

The fixed first three lines are `format=PQC-DEMO-1`, `kem=ML-KEM-768`, and `cipher=AES-256-GCM`. Their ASCII bytes, each followed by LF, are the associated authenticated data (AAD). The remaining fields are `encapsulation`, `nonce`, and `ciphertext`, encoded using padded Base64. The ciphertext includes the 16-byte authentication tag.

- ML-KEM-768 encapsulation: exactly 1,088 bytes; shared secret used as one 32-byte AES key.
- AES-GCM nonce: exactly 12 fresh random bytes per encryption; tag: 128 bits.
- Plaintext: at most 1,048,576 bytes; ciphertext including tag: 16 to 1,048,592 bytes.
- Envelope file: at most 2,097,152 bytes; each key file: at most 16,384 bytes.
- Public key: X.509 SubjectPublicKeyInfo DER. Private key: unencrypted PKCS#8 DER. Both loaders require ML-KEM-768.

Files are read into bounded memory. This application does not stream large files. It writes recovered output only after `Cipher.doFinal` authenticates the complete message. Writes use exclusive creation; cleanup of newly created incomplete files after an I/O error is best effort. Use a local directory you control; this lab does not defend against an attacker concurrently replacing directories or files.

Exit codes for direct JAR execution:

- `0`: success or `--help`.
- `2`: incorrect arguments, malformed envelope, or input size limit.
- `3`: decryption authentication/decapsulation failure.
- `4`: file access, existing output, invalid key encoding/parameter set, or another cryptographic error.

## Security boundaries and cleanup

This ML-KEM-only lab is not a hybrid TLS exchange and is not an implementation of HPKE, CMS, or another interoperable message protocol. Do not use the envelope as a replacement for those protocols. It adds no certificate validation, signatures, replay protection, key rotation service, or managed key storage.

Obtain recipient public keys through an authenticated mechanism. Protect private keys using storage and providers appropriate to your deployment; key management service (KMS) or hardware security module (HSM) support for the selected algorithms and encodings must be checked with that provider. Keeping a persistent private key means an attacker who steals it can decrypt previously saved envelopes for that key. Rotation protects future messages only after senders obtain the new authentic public key; old messages still require their original private key. Losing the only copy loses access to those messages.

The build ignores `target/`, `*.der`, and `*.pqc`. Use `./mvnw clean` to remove generated files under `target/` before repeating the exercise. Deleting files is not a promise of secure erasure on your storage device.
