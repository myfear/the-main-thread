package com.themainthread.pglite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.quarkiverse.endive.runtime.wasm.ExecutionMode;
import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;

import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/** Shares parsed and compiled code; every open database gets independent WASM memory. */
@Singleton
public class PGliteFactory {
    private static final Logger LOG = Logger.getLogger(PGliteFactory.class);
    private static final String BUNDLE_SHA256 = "c052dafc7fbfabb4453a862af5327c437944449b3ff9f93ee33a19e98c915838";

    private final WasmQuarkusContext context;
    private byte[] bundle;
    private WasmModule module;
    private Function<Instance, Machine> machineFactory;

    public PGliteFactory(@Named("pglite") WasmQuarkusContext context) {
        this.context = context;
    }

    public synchronized PGliteDatabase open(Path directory) {
        initialize();
        return new PGliteDatabase(directory, bundle, module, machineFactory);
    }

    private void initialize() {
        if (module != null) {
            return;
        }
        try (var input = PGliteFactory.class.getResourceAsStream("/pglite/pglite-wasi.tar.xz")) {
            if (input == null) {
                throw new IOException("Missing PGlite WASI bundle");
            }
            bundle = input.readAllBytes();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle));
            if (!BUNDLE_SHA256.equals(digest)) {
                throw new IOException("PGlite bundle SHA-256 mismatch: " + digest);
            }
            try (var fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
                BundleFiles.extract(bundle, fileSystem.getPath("/"));
                WasmModule parsed = Parser.parse(Files.readAllBytes(fileSystem.getPath("/tmp/pglite/bin/pglite.wasi")));
                // Endive's per-instance compiler would recompile this large module on every open().
                // Honor the extension's JVM execution setting and cache the compiled factory instead.
                machineFactory = context.getExecutionMode() == ExecutionMode.RuntimeCompiler
                        ? MachineFactoryCompiler.compile(parsed)
                        : context.getMachineFactory();
                module = parsed;
            }
            LOG.infof("PGlite WASI bundle verified; Endive execution mode: %s", context.getExecutionMode());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
