# Third-party notices

Original companion application code, tests, and the Java demo server are copyright (C) 2026 Markus Eisele and licensed under GPL-3.0-only. See [LICENSE](LICENSE). Synthetic RDF examples are part of the companion; they are not copied DBpedia records.

Third-party components retain their own copyrights and license terms. The companion's GPL license does not replace those notices.

- **Xesta 1.0.1**, by Jorge Sanchez / Vionta.net, is licensed under GNU GPL version 3. Its XPath binding API supplies the Java projection used here. [Project](https://github.com/vionta/xesta), [license](https://github.com/vionta/xesta/blob/main/LICENSE), [published source archive](https://repo.maven.apache.org/maven2/net/vionta/xml/xesta/1.0.1/xesta-1.0.1-sources.jar), [published POM](https://repo.maven.apache.org/maven2/net/vionta/xml/xesta/1.0.1/xesta-1.0.1.pom).
- **Saxon-HE 12.9**, from Saxonica, is an Xesta dependency licensed under Mozilla Public License 2.0. [Project and source](https://github.com/Saxonica/Saxon-HE), [published POM](https://repo.maven.apache.org/maven2/net/sf/saxon/Saxon-HE/12.9/Saxon-HE-12.9.pom), [Mozilla's distribution guidance](https://www.mozilla.org/en-US/MPL/2.0/FAQ/).
- **Apache Maven Wrapper 3.3.4** scripts retain their Apache License 2.0 headers. Copies of the [license](LICENSES/Apache-2.0.txt) and [upstream notice](LICENSES/Maven-Wrapper-NOTICE.txt) accompany this source. [Project](https://maven.apache.org/wrapper/).
- **Quarkus and the other resolved dependencies** retain the licenses and notices in their published artifacts and POMs. Preserve those notices when redistributing their JARs. `./mvnw dependency:tree` lists the resolved dependencies for this build.

The source links above identify upstream material. They are not a complete corresponding-source distribution or a written source offer for a binary release. A binary distributor must satisfy the source obligations described in the README and the applicable component licenses for the exact binaries being distributed.

Live DBpedia results are external data with their own attribution and licensing terms. The application reports the subject URI, and DBpedia can provide license information in its HTTP response headers. A software license does not replace data-license requirements.
