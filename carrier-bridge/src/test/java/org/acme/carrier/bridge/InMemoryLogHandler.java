package org.acme.carrier.bridge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

final class InMemoryLogHandler extends Handler {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    @Override
    public void publish(LogRecord record) {
        if (record != null) {
            messages.add(record.getMessage());
        }
    }

    @Override
    public void flush() {
        // nothing to flush
    }

    @Override
    public void close() {
        messages.clear();
    }

    void clear() {
        messages.clear();
    }

    String joinedMessages() {
        return String.join("\n", messages);
    }
}
