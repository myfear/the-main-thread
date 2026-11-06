# The Wrong Airport

A local Quarkus application that shows how a broad XPath can select a runway length from the wrong RDF subject. A deliberately unsafe test binds `9999`; the application selects Munich Airport's exact subject, validates its literals, and returns `4000` from the same synthetic document.

Companion to [The Wrong Airport: RDF/XML with Quarkus and Xesta](https://www.the-main-thread.com/p/quarkus-xesta-rdf-xml-wrong-airport).

## License and redistribution requirements

The original application code, tests, and Java demo server are **GPL-3.0-only**, copyright (C) 2026 Markus Eisele. The complete license is in [LICENSE](LICENSE). Third-party components retain their own licenses and notices; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

**Xesta 1.0.1 declares GNU GPL version 3** in its [published POM](https://repo.maven.apache.org/maven2/net/vionta/xml/xesta/1.0.1/xesta-1.0.1.pom) and [repository license](https://github.com/vionta/xesta/blob/main/LICENSE). This companion calls Xesta as a linked Java library. The [GNU GPL FAQ on GPL libraries](https://www.gnu.org/licenses/gpl-faq.html#IfLibraryIsGPL) explains that a distributed combined program must be released under GPL-compatible terms with the combination covered by the GPL. Keeping Xesta in a separate dependency JAR does not, by itself, remove that requirement.

When you redistribute this companion or a modified application that includes Xesta:

1. **Preserve notices and the license.** Keep the copyright, license, and warranty notices and provide the full GPL text to recipients. Preserve the applicable third-party notices as well.
2. **Identify modifications.** Modified versions must carry prominent notices that you changed the files, with relevant dates. License the covered combined program under GPL v3 and do not impose additional restrictions on recipients' GPL rights. See GPL sections 4, 5, and 10.
3. **Provide Corresponding Source when distributing binaries.** A JAR, native executable, or container image containing the covered program requires a compliant GPL section 6 source-delivery method. Corresponding Source includes the exact application and covered library source, your modifications, and the scripts needed to build and install the program, subject to the license's exclusions. For a download under section 6(d), offer equivalent access to the corresponding source at no further charge, with clear directions and availability for as long as required. A generic link to an upstream default branch is not a substitute for source corresponding to your binary. Other section 6 methods have their own conditions.
4. **Retain other component obligations.** Saxon-HE 12.9 uses MPL 2.0, including source-availability requirements for covered files when distributing its executable form. The Maven Wrapper scripts use Apache License 2.0; their license and upstream NOTICE are included under LICENSES/. Preserve the licenses and notices of the other dependencies you redistribute.

Running or modifying the program privately does not, by itself, require publishing source. Merely exposing an API over a network does not trigger an AGPL-style source offer under GPL v3; conveying copies is a different situation. See the [GNU FAQ on private modifications](https://www.gnu.org/licenses/gpl-faq.html#UnreleasedMods). Review the actual distribution model before adding Xesta to a proprietary product. The [full GPL v3 text](https://www.gnu.org/licenses/gpl-3.0.html) controls these requirements.

This repository supplies application source and notices. It does not claim to be a complete Corresponding Source bundle or a written source offer for an independently distributed binary. A binary distributor must assemble the required source for the exact release.

The local RDF documents are synthetic examples. Live DBpedia data has separate attribution and licensing terms; the software license does not replace them. Check the terms attached to the DBpedia dataset or response before redistributing its data.

## Prerequisites

- JDK **25**, with `JAVA_HOME` and `java` pointing to that JDK.
- Internet access for the first Maven dependency download. The included wrapper downloads Maven **3.9.16**.
- `curl`; optionally `jq` to format JSON. Remove `| jq .` from the commands if it is unavailable.

The application pins Quarkus **3.40.1.1** and Xesta **1.0.1**. It runs as a JVM application. Native executables are outside this example's scope. No database, container engine, Python, or public DBpedia access is required for the tests or local demo.

Run every command below from the `wrong-airport` directory.

## Build and test

```bash
./mvnw -B test
./mvnw -B package
```

The regular suite runs 43 tests, including application endpoint tests. To also test the packaged application:

```bash
./mvnw -B verify -DskipITs=false
```

This runs the 43 regular tests and 19 additional `@QuarkusIntegrationTest` cases against the built application. The HTTP tests start their own loopback upstream server and never call public DBpedia. Their smaller 2,048-byte limit and 500 ms timeouts make boundary cases inexpensive to reproduce.

The Quarkus fast-JAR distribution is the **whole** `target/quarkus-app/` directory. Keep its dependency JARs with `quarkus-run.jar` when moving a build.

## Run the deterministic local demo

In terminal 1, start the small Java upstream server:

```bash
java demo/FixtureServer.java
```

It binds to `127.0.0.1:9090` and serves `demo/rdf/wrong-airport-first.rdf` at `/data/Munich_Airport.rdf`.

In terminal 2, start the packaged application with the `demo` profile:

```bash
java -Dquarkus.profile=demo -jar target/quarkus-app/quarkus-run.jar
```

In terminal 3:

```bash
curl -fsS http://127.0.0.1:8080/airports/MUC | jq .
```

The synthetic response is:

```json
{
  "iata": "MUC",
  "name": "Munich Airport",
  "runwayLength": "4000",
  "sourceUri": "http://dbpedia.org/resource/Munich_Airport",
  "sourceFormat": "RDF/XML"
}
```

The subject URI identifies the RDF subject used by the sample. In demo mode it does not claim that the bytes came from live DBpedia. The runway length is the exact XML literal text; the application performs no numeric conversion or unit inference.

Only uppercase `MUC` is accepted. An unknown code returns HTTP 404 without an upstream request:

```bash
curl -i http://127.0.0.1:8080/airports/UNKNOWN
```

Stop both Java processes with Ctrl+C when finished.

## Reproduce the failure and rejection cases

The unsafe XPath exists only in the tests. This command passes by asserting that the naive mapping selects the wrong value, `9999`:

```bash
./mvnw -B -Dtest=NaiveMappingTest test
```

Its second test shows that a descendant search can also reach another subject nested inside the selected description. The corrected mapping uses direct child properties and returns `4000`.

To try another upstream document, stop the demo server and restart it with a scenario name:

```bash
java demo/FixtureServer.java 9090 ambiguous-values
```

With the application still running in the `demo` profile:

```bash
curl -i http://127.0.0.1:8080/airports/MUC
```

This returns HTTP 502 with `code` set to `AMBIGUOUS_FIELD`. Available scenarios are `wrong-airport-first`, `target-airport-first`, `missing-target`, `ambiguous-values`, `nested-other-subject`, and `xxentity`. The last document contains a DOCTYPE that the application rejects before resolving its external entity.

## Optional live DBpedia request

Stop the demo application, then start it without the profile:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

```bash
curl -fsS http://127.0.0.1:8080/airports/MUC | jq .
```

This calls `https://dbpedia.org/data/Munich_Airport.rdf`. On **26 October 2026**, the live request returned `MUC`, `Munich Airport`, and the runway literal **`4000.0`**. Live data and service availability can change. The deterministic demo's `4000` is not a claim about the live response.

## Configuration and failure handling

The application configuration is:

```properties
quarkus.http.host=127.0.0.1
quarkus.rest-client.dbpedia.url=https://dbpedia.org
quarkus.rest-client.dbpedia.connect-timeout=2000
quarkus.rest-client.dbpedia.read-timeout=5000
quarkus.rest-client.dbpedia.disable-default-mapper=true
quarkus.rest-client.dbpedia.follow-redirects=false
mainthread.dbpedia.max-bytes=4194304
mainthread.dbpedia.body-timeout=5s

%demo.quarkus.rest-client.dbpedia.url=http://127.0.0.1:9090

# Xesta logs mapped properties at INFO. Keep external literal values out of logs.
quarkus.log.category."net.vionta.xml.xesta".level=OFF
```

`quarkus.http.host` keeps this local companion on loopback. The REST Client URL selects the upstream; only the server-side `Munich_Airport` slug is supplied by the application. Callers cannot supply a URL or slug. The demo profile changes the upstream to the local Java server.

The connection timeout is 2 seconds and the client read timeout is 5 seconds. In the pinned client version, that read timeout does not bound consumption of the returned `InputStream`. `BoundedBodyReader` therefore applies a separate 5-second body-read deadline using a cancellable virtual-thread task. The caller closes the stream and response on every path. These are separate budgets, **not a total request deadline**, and neither includes all DOM parsing and mapping time.

Disabling the default exception mapper allows the fetcher to inspect upstream status codes itself. Redirects are disabled. The fetcher requires HTTP 200 with `application/rdf+xml`, requests `Accept-Encoding: identity`, and rejects any other content encoding. Automatic response decompression is left at its disabled default. TLS verification remains enabled.

The 4 MiB limit is checked against `Content-Length` when available and against the actual bytes read in every case. The reader consumes at most the limit plus one byte to detect overflow. DOM construction and the isolated copy require additional memory; this is a response-body bound, not a complete heap bound or concurrency limit.

The parser rejects DOCTYPE, external entities, external DTDs and schemas, and disables XInclude processing. Mandatory protections are checked at startup. It also limits XML element depth to 64 and attributes per element to 128. Xesta receives the application-owned DOM; its HTTP and XML parsing helpers are not used.

Logs contain fixed request identifiers, status, elapsed time, known payload size, and failure category. Xesta's own mapped-value logging is disabled. Error JSON contains a stable code and a safe message, without upstream XML or exception details.

- HTTP **404**: `UNKNOWN_AIRPORT`, rejected before fetching.
- HTTP **504**: `UPSTREAM_TIMEOUT` for connection/client or body-read timeouts.
- HTTP **502** transport failures: `UPSTREAM_STATUS`, `UPSTREAM_MEDIA_TYPE`, `UPSTREAM_ENCODING`, `UPSTREAM_TOO_LARGE`, `UPSTREAM_UNAVAILABLE`.
- HTTP **502** XML/projection failures: `MALFORMED_XML`, `UNSUPPORTED_RDF`, `MISSING_SUBJECT`, `MISSING_FIELD`, `AMBIGUOUS_FIELD`, `IDENTITY_MISMATCH`, `MAPPING_FAILED`.

An upstream 404 is a 502 here: it is a failed lookup of an application-supported resource, not evidence that the caller used an unknown code. The application implements no retry loop.

## Supported RDF/XML profile

This is a projection of a known serialization layout, not a general RDF parser:

- The root must be `rdf:RDF` in the RDF namespace and may carry namespace declarations only.
- All matching, top-level `rdf:Description` elements with the exact absolute `rdf:about` URI are retained. Their attributes may contain namespace declarations and `rdf:about` only. Inherited `xml:base` and `xml:lang`, typed nodes, `rdf:ID`, and blank-node identity are not supported for the selected subject.
- Requested values must be direct, nonblank text literals: `dbp:iata`, an explicitly tagged English `rdfs:label`, and `dbo:runwayLength`. `en` is matched case-insensitively; `en-GB`, untagged labels, and other languages are not fallbacks.
- Selected properties may declare an absolute `rdf:datatype` or `xml:lang`, but not both together. Nested elements, resource-valued properties, and unsupported attributes are rejected for those selected literals.
- Duplicate identical literal terms are accepted. Different lexical forms, datatypes, or language tags are ambiguous, even if a numeric conversion could make two values equal. The response preserves the chosen literal's text.
- Unrelated subjects and properties are ignored. The returned IATA literal must exactly equal `MUC`.

This implementation does not validate the entire RDF graph, normalize equivalent RDF terms, infer facts, resolve arbitrary RDF/XML forms, or execute SPARQL. Use RDF-aware tooling such as [Apache Jena](https://jena.apache.org/) when those are requirements.
