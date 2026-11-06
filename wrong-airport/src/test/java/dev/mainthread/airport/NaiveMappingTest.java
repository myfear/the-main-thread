// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

import net.vionta.xml.xesta.bind.annotation.Bind;
import net.vionta.xml.xesta.bind.serialize.Deserializer;

class NaiveMappingTest {
    @Test
    void broadXPathBindsTheOtherAirportsLength() throws Exception {
        var document = new SecureXmlParser().parse(Fixtures.bytes("wrong-airport-first"));
        var airport = new Deserializer().deserialize(new NaiveAirport(), document);
        assertEquals("9999", airport.getRunwayLength());
    }

    @Test
    void isolationDoesNotMakeDescendantSearchesSafe() throws Exception {
        var document = new SecureXmlParser().parse(Fixtures.bytes("nested-other-subject"));
        var isolated = new RdfSubjectIsolator().isolate(document, AirportService.SUBJECT);
        var airport = new Deserializer().deserialize(new NaiveAirport(), isolated);
        assertEquals("9999", airport.getRunwayLength());
        assertEquals("4000", new AirportXmlMapper().map(isolated, "MUC", AirportService.SUBJECT).runwayLength());
    }

    @Bind(expression = "/*:RDF")
    public static class NaiveAirport implements Serializable {
        @Bind(expression = ".//dbo:runwayLength[1]", namespaceAlias = { "dbo" },
                namespaceUris = { "http://dbpedia.org/ontology/" })
        private String runwayLength;

        public NaiveAirport() {
        }

        public String getRunwayLength() {
            return runwayLength;
        }

        public void setRunwayLength(String runwayLength) {
            this.runwayLength = runwayLength;
        }
    }
}
