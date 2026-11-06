// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.enterprise.context.ApplicationScoped;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import io.quarkus.runtime.Startup;

@Startup
@ApplicationScoped
public class SecureXmlParser {
    public SecureXmlParser() {
        newBuilder();
    }

    public Document parse(byte[] bytes) {
        try {
            return newBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (SAXException | IOException failure) {
            throw new AirportFailure(AirportFailure.Kind.MALFORMED_XML, failure);
        }
    }

    private static DocumentBuilder newBuilder() {
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("jdk.xml.maxElementDepth", "64");
            factory.setAttribute("jdk.xml.elementAttributeLimit", "128");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are disabled");
            });
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException failure) throws SAXException {
                    throw failure;
                }

                @Override
                public void fatalError(SAXParseException failure) throws SAXException {
                    throw failure;
                }
            });
            return builder;
        } catch (ParserConfigurationException | IllegalArgumentException failure) {
            throw new IllegalStateException("Required XML parser protections are unavailable", failure);
        }
    }
}
