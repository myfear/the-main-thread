// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class RdfProjectionTest {
    private final SecureXmlParser parser = new SecureXmlParser();
    private final RdfSubjectIsolator isolator = new RdfSubjectIsolator();
    private final AirportXmlMapper mapper = new AirportXmlMapper();

    @Test
    void selectsTheSameFactsRegardlessOfSubjectAndLanguageOrder() {
        var first = map(Fixtures.text("wrong-airport-first"));
        assertEquals(new AirportResponse("MUC", "Munich Airport", "4000", AirportService.SUBJECT, "RDF/XML"), first);
        assertEquals(first, map(Fixtures.text("target-airport-first")));
    }

    @Test
    void ignoresNestedFactsAboutAnotherSubject() {
        assertEquals("4000", map(Fixtures.text("nested-other-subject")).runwayLength());
    }

    @Test
    void rejectsMissingSubject() {
        fails(Fixtures.text("missing-target"), AirportFailure.Kind.MISSING_SUBJECT);
    }

    @Test
    void rejectsConflictingValuesAcrossDescriptionsOfTheSameSubject() {
        fails(Fixtures.text("ambiguous-values"), AirportFailure.Kind.AMBIGUOUS_FIELD);
    }

    @Test
    void acceptsRepeatedIdenticalTermsAcrossDescriptions() {
        String repeated = Fixtures.text("ambiguous-values").replace(">3000<", ">4000<");
        assertEquals("4000", map(repeated).runwayLength());
    }

    @Test
    void doesNotEquateDifferentDatatypesWithTheSameText() {
        String differentType = Fixtures.text("ambiguous-values").replace("<dbo:runwayLength>3000",
                "<dbo:runwayLength rdf:datatype=\"http://www.w3.org/2001/XMLSchema#double\">4000");
        fails(differentType, AirportFailure.Kind.AMBIGUOUS_FIELD);
    }

    @Test
    void rejectsConflictingEnglishNames() {
        String conflict = valid().replace("<dbp:iata>",
                "<rdfs:label xml:lang=\"en\">Different name</rdfs:label><dbp:iata>");
        fails(conflict, AirportFailure.Kind.AMBIGUOUS_FIELD);
    }

    @Test
    void requiresAnEnglishLabelWithoutChoosingAnotherLanguage() {
        fails(valid().replace("xml:lang=\"en\"", "xml:lang=\"fr\""), AirportFailure.Kind.MISSING_FIELD);
    }

    @Test
    void acceptsCaseInsensitiveEnglishLanguageTags() {
        assertEquals("Munich Airport", map(valid().replace("xml:lang=\"en\"", "xml:lang=\"EN\"")).name());
    }

    @Test
    void rejectsAnIataValueThatDoesNotMatchTheAllowlist() {
        fails(valid().replace(">MUC<", ">VGO<"), AirportFailure.Kind.IDENTITY_MISMATCH);
    }

    @Test
    void missingRunwayDoesNotFallBackToAnotherSubject() {
        fails(valid().replace("<dbo:runwayLength>4000</dbo:runwayLength>", ""), AirportFailure.Kind.MISSING_FIELD);
    }

    @Test
    void preservesRunwayLexicalFormWithoutNumericCoercion() {
        assertEquals("4000.0", map(valid().replace(">4000<", ">4000.0<")).runwayLength());
    }

    @Test
    void namespaceAliasesDoNotEstablishIdentity() {
        String renamed = valid().replace("rdf:", "graph:").replace("xmlns:rdf=", "xmlns:graph=")
                .replace("dbo:", "ontology:").replace("xmlns:dbo=", "xmlns:ontology=");
        assertEquals("4000", map(renamed).runwayLength());
    }

    @Test
    void rejectsPropertiesWithAnUnexpectedNamespaceUri() {
        fails(valid().replace("http://dbpedia.org/ontology/", "https://example.invalid/ontology/"),
                AirportFailure.Kind.MISSING_FIELD);
    }

    @Test
    void rejectsResourceValuedProperties() {
        fails(valid().replace("<dbo:runwayLength>4000</dbo:runwayLength>",
                "<dbo:runwayLength rdf:resource=\"https://example.invalid/value\"/>"), AirportFailure.Kind.UNSUPPORTED_RDF);
    }

    @Test
    void rejectsNestedElementContentInsteadOfFlatteningIt() {
        fails(valid().replace(">4000<", "><rdf:Description rdf:about=\"https://example.invalid/other\">"
                + "<dbo:runwayLength>9999</dbo:runwayLength></rdf:Description><"), AirportFailure.Kind.UNSUPPORTED_RDF);
    }

    @Test
    void rejectsInheritedLanguageAndBaseContext() {
        fails(valid().replace("<rdf:RDF ", "<rdf:RDF xml:lang=\"en\" "), AirportFailure.Kind.UNSUPPORTED_RDF);
        fails(valid().replace("<rdf:RDF ", "<rdf:RDF xml:base=\"https://example.invalid/\" "),
                AirportFailure.Kind.UNSUPPORTED_RDF);
    }

    @Test
    void rejectsPropertyAttributeSerializationOnTheSelectedSubject() {
        fails(valid().replace("rdf:about=\"" + AirportService.SUBJECT + "\"",
                "dbp:iata=\"VGO\" rdf:about=\"" + AirportService.SUBJECT + "\""), AirportFailure.Kind.UNSUPPORTED_RDF);
    }

    @Test
    void requiresTheRdfRootNamespace() {
        fails(valid().replace("rdf:RDF", "dbo:RDF"), AirportFailure.Kind.UNSUPPORTED_RDF);
    }

    private static String valid() {
        return Fixtures.text("wrong-airport-first");
    }

    private AirportResponse map(String xml) {
        return mapper.map(isolator.isolate(parser.parse(xml.getBytes(StandardCharsets.UTF_8)), AirportService.SUBJECT),
                "MUC", AirportService.SUBJECT);
    }

    private void fails(String xml, AirportFailure.Kind kind) {
        assertEquals(kind, assertThrows(AirportFailure.class, () -> map(xml)).kind());
    }
}
