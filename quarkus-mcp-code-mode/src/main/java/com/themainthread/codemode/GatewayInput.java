package com.themainthread.codemode;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

/** Bounds each stdin line before the extension decodes it; the extension owns MCP dispatch. */
final class GatewayInput extends InputStream {
    private final InputStream source;
    private final int maximumBytes;
    private final CountDownLatch started = new CountDownLatch(1);
    private ByteArrayInputStream line = new ByteArrayInputStream(new byte[0]);
    private boolean ended;

    GatewayInput(InputStream source, int maximumBytes) {
        this.source = new BufferedInputStream(source);
        this.maximumBytes = maximumBytes;
    }

    void open() {
        started.countDown();
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) return 0;
        try {
            started.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting gateway startup", e);
        }
        if (line.available() == 0 && !ended) {
            var bytes = new ByteArrayOutputStream();
            for (int value; (value = source.read()) != -1;) {
                if (value == '\n') {
                    bytes.write(value);
                    line = new ByteArrayInputStream(bytes.toByteArray());
                    return line.read(buffer, offset, length);
                }
                if (bytes.size() == maximumBytes) {
                    ended = true;
                    return -1; // End the single stdio session on an oversized frame.
                }
                bytes.write(value);
            }
            ended = true;
        }
        return line.read(buffer, offset, length);
    }
}
