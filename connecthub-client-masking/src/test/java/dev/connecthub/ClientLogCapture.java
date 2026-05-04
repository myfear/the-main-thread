package dev.connecthub;

import java.io.StringWriter;
import java.util.logging.Level;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.WriterHandler;

/**
 * Captures {@link org.jboss.resteasy.reactive.client.logging.DefaultClientLogger} output for tests.
 */
final class ClientLogCapture implements AutoCloseable {

    static final String DEFAULT_CLIENT_LOGGER_CATEGORY = "org.jboss.resteasy.reactive.client.logging.DefaultClientLogger";

    private final WriterHandler handler;
    private final StringWriter writer;
    private final Logger logger;

    ClientLogCapture() {
        writer = new StringWriter();
        handler = new WriterHandler();
        handler.setWriter(writer);
        handler.setFormatter(new PatternFormatter("%m%n"));
        handler.setLevel(Level.ALL);
        logger = LogContext.getLogContext().getLogger(DEFAULT_CLIENT_LOGGER_CATEGORY);
        logger.addHandler(handler);
        if (!logger.isLoggable(Level.INFO)) {
            logger.setLevel(Level.INFO);
        }
    }

    String captured() {
        handler.flush();
        return writer.toString();
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }
}
