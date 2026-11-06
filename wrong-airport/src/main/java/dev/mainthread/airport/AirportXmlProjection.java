// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.Serializable;

import net.vionta.xml.xesta.bind.annotation.Bind;

@Bind(expression = "/rdf:RDF", namespaceAlias = { "rdf" },
        namespaceUris = { "http://www.w3.org/1999/02/22-rdf-syntax-ns#" })
public class AirportXmlProjection implements Serializable {
    @Bind(expression = "rdf:Description/dbp:iata", namespaceAlias = { "rdf", "dbp" },
            namespaceUris = { "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "http://dbpedia.org/property/" })
    private String iata;

    @Bind(expression = "rdf:Description/rdfs:label[lower-case(@xml:lang)='en']",
            namespaceAlias = { "rdf", "rdfs", "xml" }, namespaceUris = {
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "http://www.w3.org/2000/01/rdf-schema#",
                    "http://www.w3.org/XML/1998/namespace" })
    private String name;

    @Bind(expression = "rdf:Description/dbo:runwayLength", namespaceAlias = { "rdf", "dbo" },
            namespaceUris = { "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "http://dbpedia.org/ontology/" })
    private String runwayLength;

    public AirportXmlProjection() {
    }

    public String getIata() {
        return iata;
    }

    public void setIata(String iata) {
        this.iata = iata;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRunwayLength() {
        return runwayLength;
    }

    public void setRunwayLength(String runwayLength) {
        this.runwayLength = runwayLength;
    }
}
