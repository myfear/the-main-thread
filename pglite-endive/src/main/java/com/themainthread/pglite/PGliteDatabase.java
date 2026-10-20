package com.themainthread.pglite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.jboss.logging.Logger;

import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Machine;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmModule;

/** One serialized PostgreSQL session, with snapshots written after clean shutdown. */
public final class PGliteDatabase implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PGliteDatabase.class);
    private final UUID instanceId = UUID.randomUUID();
    private final ByteArrayOutputStream guestLog = new ByteArrayOutputStream();
    private DatabaseStorage storage;
    private WasiPreview1 wasi;
    private Instance instance;
    private boolean closed;
    private boolean dead;
    private String transactionStatus = "I";

    PGliteDatabase(Path directory, byte[] bundle, WasmModule module, Function<Instance, Machine> machineFactory) {
        try {
            storage = new DatabaseStorage(directory, bundle);
            var options = WasiOptions.builder()
                    .withArguments(List.of("/tmp/pglite/bin/postgres", "--single", "postgres"))
                    .withEnvironment("PREFIX", "/tmp/pglite")
                    .withEnvironment("PGDATA", "/tmp/pglite/base")
                    .withEnvironment("PGSYSCONFDIR", "/tmp/pglite")
                    .withEnvironment("PGUSER", "postgres")
                    .withEnvironment("PGDATABASE", "template1")
                    .withEnvironment("REPL", "N")
                    .withEnvironment("TZ", "UTC")
                    .withEnvironment("PGTZ", "UTC")
                    .withEnvironment("PATH", "/tmp/pglite/bin")
                    .withStdout(guestLog).withStderr(guestLog)
                    .withStdin(new ByteArrayInputStream(new byte[0]))
                    .withDirectory("/tmp", storage.fileSystem.getPath("/tmp"))
                    .withDirectory("/dev", storage.fileSystem.getPath("/dev"))
                    .build();
            wasi = WasiPreview1.builder().withOptions(options).build();
            instance = Instance.builder(module).withStart(false).withMachineFactory(machineFactory)
                    .withImportValues(ImportValues.builder().addFunction(wasi.toHostFunctions()).build()).build();
            call("_start");
            for (String suffix : List.of(".in", ".lock.in", ".out", ".lock.out")) {
                Files.deleteIfExists(ioPath(suffix));
            }
            long[] initialization = call("pgl_initdb");
            if (initialization.length != 1 || (initialization[0] & 1) != 0) {
                throw new IllegalStateException("PGlite initdb reported failure: " + guestLog);
            }
            call("pgl_backend");
            call("use_wire", 1);
            authenticate();
            // The WASI resume path inherits initdb's catalog-only search path.
            // Apply PostgreSQL's normal session default on fresh and resumed databases.
            execute("SET search_path TO \"$user\", public");
            LOG.infof("PGlite database initialized: %s", instanceId);
            guestLog.reset();
        } catch (IOException | RuntimeException e) {
            LOG.errorf("PGlite initialization failed: %s", guestLog.toString());
            release();
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    public UUID instanceId() {
        return instanceId;
    }

    public synchronized String transactionStatus() {
        return transactionStatus;
    }

    public synchronized QueryResult execute(String sql) {
        requireOpen();
        try {
            PostgresWire.Reply reply = PostgresWire.decode(send(PostgresWire.query(sql), false));
            transactionStatus = reply.result().transactionStatus();
            guestLog.reset();
            if (reply.error() != null) {
                throw reply.error();
            }
            return reply.result();
        } catch (IOException e) {
            dead = true;
            throw new UncheckedIOException(e);
        }
    }

    /** Holds the session lock for the entire synchronous callback, including commit or rollback. */
    public synchronized <T> T transaction(Function<PGliteDatabase, T> work) {
        requireOpen();
        if (!"I".equals(transactionStatus)) {
            throw new IllegalStateException("A transaction is already active");
        }
        execute("BEGIN");
        try {
            T value = work.apply(this);
            if (!"T".equals(transactionStatus)) {
                throw new IllegalStateException("Transaction callback must leave its transaction open and valid");
            }
            execute("COMMIT");
            return value;
        } catch (RuntimeException | Error e) {
            if (!dead && !closed && !"I".equals(transactionStatus)) {
                try {
                    execute("ROLLBACK");
                } catch (RuntimeException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        }
    }

    private void authenticate() throws IOException {
        byte[] reply = send(PostgresWire.startup(), true);
        for (PostgresWire.Frame frame : PostgresWire.frames(reply)) {
            if (frame.type() == 'E') {
                throw PostgresWire.error(frame.body());
            }
            if (frame.type() == 'R') {
                int code = frame.body().getInt();
                if (code == 5) {
                    byte[] salt = new byte[4];
                    frame.body().get(salt);
                    PostgresWire.Reply authenticated = PostgresWire.decode(send(PostgresWire.password(salt), false));
                    if (authenticated.error() != null) {
                        throw authenticated.error();
                    }
                    transactionStatus = authenticated.result().transactionStatus();
                    return;
                }
                if (code != 0) {
                    throw new IllegalStateException("Unsupported PostgreSQL authentication method: " + code);
                }
            }
        }
        PostgresWire.Reply authenticated = PostgresWire.decode(reply);
        if (authenticated.error() != null) {
            throw authenticated.error();
        }
    }

    private byte[] send(byte[] payload, boolean authenticating) throws IOException {
        call("use_wire", 1);
        // Drain stale output before opening a new request/reply exchange.
        consume(50, new ByteArrayOutputStream());
        Files.write(ioPath(".lock.in"), payload);
        Files.move(ioPath(".lock.in"), ioPath(".in"));
        var output = new ByteArrayOutputStream();
        consume(200, output);
        // A fixed number of ticks drains deferred work; wasipg_flush only surfaces buffered output.
        for (int i = 0; i < 2; i++) {
            tick(output);
        }
        call("wasipg_flush");
        drain(output);
        for (int i = 0; i < 20 && !PostgresWire.complete(output.toByteArray(), authenticating); i++) {
            tick(output);
            call("wasipg_flush");
            drain(output);
        }
        if (!PostgresWire.complete(output.toByteArray(), authenticating)) {
            dead = true;
            throw new IllegalStateException("PGlite exchange did not finish: " + guestLog);
        }
        return output.toByteArray();
    }

    private void consume(int budget, ByteArrayOutputStream output) throws IOException {
        for (int i = 0; i < budget; i++) {
            if (!Files.exists(ioPath(".in"))) {
                drain(output);
                return;
            }
            tick(output);
        }
        dead = true;
        throw new IllegalStateException("PGlite did not consume the request");
    }

    private void tick(ByteArrayOutputStream output) throws IOException {
        call("interactive_one");
        drain(output);
    }

    private void drain(ByteArrayOutputStream output) throws IOException {
        Path reply = ioPath(".out");
        if (Files.exists(reply)) {
            output.write(Files.readAllBytes(reply));
            Files.delete(reply);
            Files.deleteIfExists(ioPath(".lock.out"));
        }
    }

    private Path ioPath(String suffix) {
        return storage.guestData.resolve(".s.PGSQL.5432" + suffix);
    }

    private long[] call(String name, long... arguments) {
        try {
            return instance.export(name).apply(arguments);
        } catch (RuntimeException e) {
            dead = true;
            throw new IllegalStateException("PGlite trapped in " + name + ": " + guestLog, e);
        }
    }

    private void requireOpen() {
        if (closed || dead) {
            throw new IllegalStateException("PGlite database is closed or trapped");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            if (!dead) {
                if (!"I".equals(transactionStatus)) {
                    execute("ROLLBACK");
                }
                execute("CHECKPOINT");
                call("pgl_shutdown");
                storage.persist();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            closed = true;
            release();
        }
    }

    private void release() {
        if (wasi != null) {
            wasi.close();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
