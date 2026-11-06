// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class AirportService {
    private static final Logger LOG = Logger.getLogger(AirportService.class);
    static final String SUBJECT = "http://dbpedia.org/resource/Munich_Airport";
    private final DbpediaRdfFetcher fetcher;
    private final SecureXmlParser parser;
    private final RdfSubjectIsolator isolator;
    private final AirportXmlMapper mapper;

    public AirportService(DbpediaRdfFetcher fetcher, SecureXmlParser parser, RdfSubjectIsolator isolator,
            AirportXmlMapper mapper) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.isolator = isolator;
        this.mapper = mapper;
    }

    public AirportResponse lookup(String code) {
        if (!"MUC".equals(code)) {
            throw new AirportFailure(AirportFailure.Kind.UNKNOWN_AIRPORT);
        }
        byte[] bytes = fetcher.fetch("MUC", "Munich_Airport", SUBJECT);
        try {
            return mapper.map(isolator.isolate(parser.parse(bytes), SUBJECT), "MUC", SUBJECT);
        } catch (AirportFailure failure) {
            LOG.warnf("RDF projection code=MUC subject=%s bytes=%d outcome=%s", SUBJECT, bytes.length, failure.kind());
            throw failure;
        }
    }
}
