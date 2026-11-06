// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class BoundedBodyReader {
    private BoundedBodyReader() {
    }

    static byte[] read(InputStream input, int maxBytes, long timeoutMillis) throws IOException {
        var task = new FutureTask<>(() -> input.readNBytes(maxBytes + 1));
        Thread.ofVirtual().name("rdf-body-read").start(task);
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            throw new SocketTimeoutException("RDF body read deadline exceeded");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("RDF body read interrupted", failure);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("RDF body read failed", failure.getCause());
        } finally {
            // Interrupts the pinned client's blocking stream wait on timeout.
            task.cancel(true);
        }
    }
}
