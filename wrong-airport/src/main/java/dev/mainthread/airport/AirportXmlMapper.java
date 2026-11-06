// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;

import jakarta.enterprise.context.ApplicationScoped;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.vionta.xml.xesta.bind.serialize.Deserializer;

@ApplicationScoped
public class AirportXmlMapper {
    private record Literal(String value, String datatype, String language) {
    }

    public AirportResponse map(Document isolated, String expectedIata, String subject) {
        Literal iata = required(isolated, "http://dbpedia.org/property/", "iata", false);
        Literal name = required(isolated, "http://www.w3.org/2000/01/rdf-schema#", "label", true);
        Literal length = required(isolated, "http://dbpedia.org/ontology/", "runwayLength", false);
        if (!expectedIata.equals(iata.value())) {
            throw new AirportFailure(AirportFailure.Kind.IDENTITY_MISMATCH);
        }
        try {
            var projection = new Deserializer().deserialize(new AirportXmlProjection(), isolated);
            if (!iata.value().equals(projection.getIata()) || !name.value().equals(projection.getName())
                    || !length.value().equals(projection.getRunwayLength())) {
                throw new AirportFailure(AirportFailure.Kind.MAPPING_FAILED);
            }
            return new AirportResponse(projection.getIata(), projection.getName(), projection.getRunwayLength(),
                    subject, "RDF/XML");
        } catch (AirportFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new AirportFailure(AirportFailure.Kind.MAPPING_FAILED, failure);
        }
    }

    private static Literal required(Document isolated, String namespace, String localName, boolean englishOnly) {
        Set<Literal> values = new LinkedHashSet<>();
        for (Node node = isolated.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element description)) {
                continue;
            }
            for (Node child = description.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element property) || !RdfSubjectIsolator.named(property, namespace, localName)) {
                    continue;
                }
                String language = property.getAttributeNS(XMLConstants.XML_NS_URI, "lang").toLowerCase(Locale.ROOT);
                if (!englishOnly || language.equals("en")) {
                    values.add(literal(property, language));
                }
            }
        }
        if (values.isEmpty()) {
            throw new AirportFailure(AirportFailure.Kind.MISSING_FIELD);
        }
        if (values.size() > 1) {
            throw new AirportFailure(AirportFailure.Kind.AMBIGUOUS_FIELD);
        }
        return values.iterator().next();
    }

    private static Literal literal(Element property, String language) {
        for (int i = 0; i < property.getAttributes().getLength(); i++) {
            Node attribute = property.getAttributes().item(i);
            String namespace = attribute.getNamespaceURI();
            boolean allowed = XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)
                    || (XMLConstants.XML_NS_URI.equals(namespace) && "lang".equals(attribute.getLocalName()))
                    || (RdfSubjectIsolator.RDF.equals(namespace) && "datatype".equals(attribute.getLocalName()));
            if (!allowed) {
                throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
            }
        }
        for (Node child = property.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element || child.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
            }
        }
        String datatype = property.getAttributeNS(RdfSubjectIsolator.RDF, "datatype");
        if (!datatype.isEmpty()) {
            try {
                if (!language.isEmpty() || !URI.create(datatype).isAbsolute()) {
                    throw new IllegalArgumentException("Unsupported literal datatype");
                }
            } catch (IllegalArgumentException failure) {
                throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
            }
        }
        String value = property.getTextContent();
        if (value.isBlank()) {
            throw new AirportFailure(AirportFailure.Kind.MISSING_FIELD);
        }
        return new Literal(value, datatype, language);
    }
}
