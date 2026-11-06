// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import javax.xml.XMLConstants;

import jakarta.enterprise.context.ApplicationScoped;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@ApplicationScoped
public class RdfSubjectIsolator {
    public static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    public Document isolate(Document source, String subject) {
        Element sourceRoot = source.getDocumentElement();
        if (!named(sourceRoot, RDF, "RDF")) {
            throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
        }
        requireAttributes(sourceRoot, false);
        Document result = source.getImplementation().createDocument(null, null, null);
        Element root = (Element) result.importNode(sourceRoot, false);
        result.appendChild(root);
        int matches = 0;
        for (Node node = sourceRoot.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element description && subject.equals(description.getAttributeNS(RDF, "about"))) {
                if (!named(description, RDF, "Description") || description.hasAttributeNS(RDF, "nodeID")
                        || description.hasAttributeNS(RDF, "ID")) {
                    throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
                }
                requireAttributes(description, true);
                root.appendChild(result.importNode(description, true));
                matches++;
            }
        }
        if (matches == 0) {
            throw new AirportFailure(AirportFailure.Kind.MISSING_SUBJECT);
        }
        return result;
    }

    static boolean named(Element element, String namespace, String localName) {
        return namespace.equals(element.getNamespaceURI()) && localName.equals(element.getLocalName());
    }

    private static void requireAttributes(Element element, boolean description) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attribute = element.getAttributes().item(i);
            boolean allowed = XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
                    || (description && RDF.equals(attribute.getNamespaceURI())
                            && "about".equals(attribute.getLocalName()));
            if (!allowed) {
                throw new AirportFailure(AirportFailure.Kind.UNSUPPORTED_RDF);
            }
        }
    }
}
