// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AirportEndpointContract {
    @BeforeEach
    void resetUpstream() {
        FakeDbpedia.reset();
    }

    @Test
    void returnsSubjectScopedFactsThroughTheRealHttpClient() {
        success();
        assertEquals("GET", FakeDbpedia.method);
        assertEquals("/data/Munich_Airport.rdf", FakeDbpedia.path);
        assertEquals("application/rdf+xml", FakeDbpedia.accept);
        assertEquals("identity", FakeDbpedia.acceptEncoding);
    }

    @Test
    void reorderedDescriptionsAndLanguagesGiveTheSameResponse() {
        FakeDbpedia.fixture("target-airport-first");
        success();
    }

    @Test
    void nestedSubjectCannotSupplyTheRunwayLength() {
        FakeDbpedia.fixture("nested-other-subject");
        success();
    }

    @Test
    void unknownCodeDoesNotCallTheUpstream() {
        given().get("/airports/UNKNOWN").then().statusCode(404).body("code", equalTo("UNKNOWN_AIRPORT"));
        assertEquals(0, FakeDbpedia.calls.get());
    }

    @Test
    void upstream404IsNotAnUnknownAirport() {
        FakeDbpedia.response(404, "text/plain", bytes("private upstream message"), true);
        failure(502, "UPSTREAM_STATUS");
    }

    @Test
    void upstream500IsControlledAndDoesNotLeakItsBody() {
        FakeDbpedia.response(500, "text/plain", bytes("private upstream message"), true);
        failure(502, "UPSTREAM_STATUS");
    }

    @Test
    void rejectsHtmlWithA200Status() {
        FakeDbpedia.response(200, "text/html", bytes("<html>private upstream message</html>"), true);
        failure(502, "UPSTREAM_MEDIA_TYPE");
    }

    @Test
    void rejectsWildcardResponseMediaTypes() {
        for (String mediaType : List.of("*/*", "application/*", "application/*+xml")) {
            FakeDbpedia.response(200, mediaType, Fixtures.bytes("wrong-airport-first"), true);
            failure(502, "UPSTREAM_MEDIA_TYPE");
        }
    }

    @Test
    void rejectsMalformedXml() {
        FakeDbpedia.response(200, "application/rdf+xml", bytes("<rdf:RDF>private upstream message"), true);
        failure(502, "MALFORMED_XML");
    }

    @Test
    void rejectsMissingSubject() {
        FakeDbpedia.fixture("missing-target");
        failure(502, "MISSING_SUBJECT");
    }

    @Test
    void rejectsAmbiguousValues() {
        FakeDbpedia.fixture("ambiguous-values");
        failure(502, "AMBIGUOUS_FIELD");
    }

    @Test
    void rejectsOversizedChunkedBodyWithoutContentLength() {
        FakeDbpedia.response(200, "application/rdf+xml", new byte[2049], true);
        failure(502, "UPSTREAM_TOO_LARGE");
    }

    @Test
    void acceptsAResponseExactlyAtTheByteLimit() {
        byte[] xml = Fixtures.bytes("wrong-airport-first");
        byte[] padded = java.util.Arrays.copyOf(xml, 2048);
        java.util.Arrays.fill(padded, xml.length, padded.length, (byte) ' ');
        FakeDbpedia.response(200, "application/rdf+xml", padded, true);
        success();
    }

    @Test
    void rejectsOversizedDeclaredBody() {
        FakeDbpedia.response(200, "application/rdf+xml", new byte[2049], false);
        failure(502, "UPSTREAM_TOO_LARGE");
    }

    @Test
    void doesNotFollowRedirects() {
        FakeDbpedia.reply = new FakeDbpedia.Reply(302, "application/rdf+xml", new byte[0], true,
                Map.of("Location", FakeDbpedia.baseUrl + "/external-entity"), 0, 0);
        failure(502, "UPSTREAM_STATUS");
        assertEquals(1, FakeDbpedia.calls.get());
        assertEquals(0, FakeDbpedia.externalEntityCalls.get());
    }

    @Test
    void refusesCompressedResponses() {
        FakeDbpedia.reply = new FakeDbpedia.Reply(200, "application/rdf+xml", new byte[100], true,
                Map.of("Content-Encoding", "gzip"), 0, 0);
        failure(502, "UPSTREAM_ENCODING");
    }

    @Test
    void timesOutWaitingForHeaders() {
        FakeDbpedia.reply = new FakeDbpedia.Reply(200, "application/rdf+xml", Fixtures.bytes("wrong-airport-first"),
                true, Map.of(), 1500, 0);
        failure(504, "UPSTREAM_TIMEOUT");
        assertEquals(1, FakeDbpedia.calls.get());
    }

    @Test
    void timesOutReadingAStalledBody() {
        FakeDbpedia.reply = new FakeDbpedia.Reply(200, "application/rdf+xml", Fixtures.bytes("wrong-airport-first"),
                true, Map.of(), 0, 1500);
        failure(504, "UPSTREAM_TIMEOUT");
    }

    @Test
    void doctypeCannotReadTheExternalEntityServer() {
        String xml = Fixtures.text("xxentity").replace("http://127.0.0.1:9/must-not-be-read",
                FakeDbpedia.baseUrl + "/external-entity");
        FakeDbpedia.response(200, "application/rdf+xml", bytes(xml), true);
        failure(502, "MALFORMED_XML");
        assertEquals(0, FakeDbpedia.externalEntityCalls.get());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void success() {
        given().accept("application/json").get("/airports/MUC").then().statusCode(200)
                .body("iata", equalTo("MUC"))
                .body("name", equalTo("Munich Airport"))
                .body("runwayLength", equalTo("4000"))
                .body("sourceUri", equalTo("http://dbpedia.org/resource/Munich_Airport"))
                .body("sourceFormat", equalTo("RDF/XML"));
    }

    private static void failure(int status, String code) {
        given().accept("application/json").get("/airports/MUC").then().statusCode(status)
                .body("code", equalTo(code))
                .body(not(containsString("private upstream message")))
                .body(not(containsString("stackTrace")));
    }
}
