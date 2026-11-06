// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DbpediaRdfFetcher {
    private static final Logger LOG = Logger.getLogger(DbpediaRdfFetcher.class);
    private static final MediaType RDF_XML = new MediaType("application", "rdf+xml");
    private final DbpediaClient client;
    private final int maxBytes;
    private final long bodyTimeoutMillis;

    public DbpediaRdfFetcher(@RestClient DbpediaClient client, DbpediaConfig config) {
        this.client = client;
        this.maxBytes = config.maxBytes();
        this.bodyTimeoutMillis = config.bodyTimeout().toMillis();
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("mainthread.dbpedia.max-bytes must be between 1 and 2147483646");
        }
        if (bodyTimeoutMillis < 1) {
            throw new IllegalArgumentException("mainthread.dbpedia.body-timeout must be at least 1ms");
        }
    }

    public byte[] fetch(String code, String resource, String subject) {
        long started = System.nanoTime();
        int status = 0;
        int bytesRead = 0;
        String outcome = "OK";
        try (var response = client.fetch(resource)) {
            status = response.getStatus();
            if (status != 200) {
                throw new AirportFailure(AirportFailure.Kind.UPSTREAM_STATUS);
            }
            var mediaType = response.getMediaType();
            if (mediaType == null || !RDF_XML.getType().equalsIgnoreCase(mediaType.getType())
                    || !RDF_XML.getSubtype().equalsIgnoreCase(mediaType.getSubtype())) {
                throw new AirportFailure(AirportFailure.Kind.UPSTREAM_MEDIA_TYPE);
            }
            String encoding = response.getHeaderString("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                throw new AirportFailure(AirportFailure.Kind.UPSTREAM_ENCODING);
            }
            rejectLargeContentLength(response.getHeaderString("Content-Length"));
            try (var input = response.getEntity()) {
                if (input == null) {
                    throw new AirportFailure(AirportFailure.Kind.MALFORMED_XML);
                }
                byte[] bytes = BoundedBodyReader.read(input, maxBytes, bodyTimeoutMillis);
                bytesRead = bytes.length;
                if (bytesRead > maxBytes) {
                    throw new AirportFailure(AirportFailure.Kind.UPSTREAM_TOO_LARGE);
                }
                return bytes;
            }
        } catch (AirportFailure failure) {
            outcome = failure.kind().name();
            throw failure;
        } catch (IOException | ProcessingException | IllegalArgumentException failure) {
            var kind = isTimeout(failure) ? AirportFailure.Kind.UPSTREAM_TIMEOUT
                    : AirportFailure.Kind.UPSTREAM_UNAVAILABLE;
            outcome = kind.name();
            throw new AirportFailure(kind, failure);
        } finally {
            LOG.infof("RDF fetch code=%s subject=%s status=%d bytes=%d elapsedMs=%d outcome=%s",
                    code, subject, status, bytesRead, (System.nanoTime() - started) / 1_000_000, outcome);
        }
    }

    private void rejectLargeContentLength(String header) {
        if (header == null) {
            return;
        }
        long length = Long.parseLong(header);
        if (length < 0) {
            throw new IllegalArgumentException("Negative Content-Length");
        }
        if (length > maxBytes) {
            throw new AirportFailure(AirportFailure.Kind.UPSTREAM_TOO_LARGE);
        }
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
