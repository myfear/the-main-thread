package dev.themainthread.invoicerecon.support;

import java.util.Optional;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressNotification;
import io.quarkiverse.mcp.server.ProgressToken;
import io.quarkiverse.mcp.server.ProgressTracker;

public final class NoopProgress implements Progress {

    @Override
    public Optional<ProgressToken> token() {
        return Optional.empty();
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        throw new UnsupportedOperationException("Noop progress");
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        throw new UnsupportedOperationException("Noop progress");
    }
}
