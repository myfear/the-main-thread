// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

public final class AirportFailure extends RuntimeException {
    public enum Kind {
        UNKNOWN_AIRPORT(404, "Unknown airport code."),
        UPSTREAM_STATUS(502, "The RDF source returned an unsuccessful HTTP status."),
        UPSTREAM_MEDIA_TYPE(502, "The RDF source did not return application/rdf+xml."),
        UPSTREAM_ENCODING(502, "The RDF source returned an unsupported content encoding."),
        UPSTREAM_TOO_LARGE(502, "The RDF response exceeds the configured size limit."),
        UPSTREAM_TIMEOUT(504, "Timed out reading the RDF source."),
        UPSTREAM_UNAVAILABLE(502, "Could not read the RDF source."),
        MALFORMED_XML(502, "The RDF source returned invalid or disallowed XML."),
        UNSUPPORTED_RDF(502, "The RDF source uses an unsupported XML layout."),
        MISSING_SUBJECT(502, "The RDF source does not describe the requested subject."),
        MISSING_FIELD(502, "The requested subject is missing a required literal."),
        AMBIGUOUS_FIELD(502, "The requested subject has multiple distinct values for a required literal."),
        IDENTITY_MISMATCH(502, "The requested subject has an unexpected IATA code."),
        MAPPING_FAILED(502, "Could not map the validated RDF literals.");

        private final int status;
        private final String message;

        Kind(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int status() {
            return status;
        }

        public String message() {
            return message;
        }
    }

    private final Kind kind;

    public AirportFailure(Kind kind) {
        this(kind, null);
    }

    public AirportFailure(Kind kind, Throwable cause) {
        super(kind.message(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
