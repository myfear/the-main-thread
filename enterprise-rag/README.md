# Local Java RAG with Quarkus and Docling

Convert five fictional CloudX PDFs to Markdown, split them into searchable chunks,
store their embeddings in PostgreSQL with pgvector, and answer questions with Ollama.
Every answer includes the text retrieved for that request, its PDF filename, and its
zero-based segment number.

Read the maintained tutorial: [Build a Local Java RAG Pipeline with Quarkus and Docling](https://www.the-main-thread.com/p/enterprise-rag-quarkus-docling).

## Prerequisites

- JDK 25. The Maven wrapper downloads Maven 3.9.11.
- Podman with a running machine on macOS or Windows, and Docker API compatibility
  configured for [Quarkus Dev Services](https://quarkus.io/guides/podman).
- A running [Ollama](https://ollama.com/) server on `localhost:11434`.
- Git, curl, and jq for the commands in this README.
- Space for the Docling CPU image (about 5 GB unpacked on ARM64), the database image,
  the chat model (about 13 GB), and the embedding model (about 63 MB). Allow extra
  space for image extraction and Maven dependencies.

This JVM workflow uses Quarkus platform 3.40.1.1, its managed LangChain4j extensions
1.13.3, Quarkus Docling 1.5.1, and Docling Serve CPU 1.35.0. It was exercised on an
Apple M4 Max with 64 GB RAM and a Podman VM with 8 CPUs and 10 GB RAM. This is the
validation machine, not a measured minimum. Native executables are outside this example.

The PDFs are synthetic demonstration data. Their prices and sales advice do not
describe a real product. OCR is disabled because these fixtures have text layers;
scanned documents require separate OCR configuration and verification.

## Run the development workflow

From this companion directory:

```bash
ollama pull gpt-oss:20b
ollama pull granite-embedding:30m
curl --fail http://localhost:11434/api/version
podman info
./mvnw quarkus:dev
```

If Ollama is not already running, start `ollama serve` in another terminal first.
Quarkus starts Docling and pgvector Dev Services. Keep this terminal open. The first
run downloads the service images. For rootless Podman, follow the Quarkus Podman
guide's Testcontainers settings, including `TESTCONTAINERS_RYUK_DISABLED=true` where
required. Do not configure an external database URL or Docling URL for this path.

In a second terminal, inspect ingestion:

```bash
curl --fail --silent http://localhost:8080/ingestion | jq
curl --silent --output /dev/null --write-out '%{http_code}\n' http://localhost:8080/q/health/ready
```

Wait for `status: READY`, `documents: 5`, and readiness HTTP 200. HTTP becomes
available before ingestion completes. `/bot`, `/retrieval`, and `/chunks` return
503 during `INDEXING` and after `FAILED` or `EMPTY`. `/ingestion` reports progress;
the application log contains the failure cause. Restart after fixing the cause.

## Inspect one answer

```bash
curl --fail --silent http://localhost:8080/chunks | jq '.[] | select(.file == "pricing_packaging_guide_2024.pdf")'
curl --fail --silent --get --data-urlencode 'q=What is the monthly Starter-tier price?' http://localhost:8080/retrieval | jq
curl --fail --silent --get --data-urlencode 'q=What is the monthly Starter-tier price?' http://localhost:8080/bot | jq
curl --fail --silent --get --data-urlencode 'q=What will the Starter tier cost in 2028?' http://localhost:8080/bot | jq
```

The known answer is $499 per month in the fictional 2024 guide. Check both the
generated answer and the retrieved pricing passage. The unsupported future-price
question is expected to return `not_found`; this checks one example, not every
possible hallucination. A model can still produce an unsupported answer from weak
but non-empty evidence.

Converted Markdown is written to `converted/<filename>.pdf.md`. Compare it with
the original PDF and `/chunks`. `DocumentBySentenceSplitter(600, 80)` uses
characters, including 80 characters of overlap; it does not count tokens or use
Docling's document structure to choose semantic boundaries.

`/retrieval` performs vector retrieval without generation. `/bot` keeps the
`response` field and adds `status` and `sources`. These sources are retrieved
excerpts, not a guarantee that every generated claim follows from them. An empty
retrieval result returns HTTP 200 with `status: not_found` and an empty source
list before the generation model is called. Model abstention also returns
`not_found`, with the excerpts it was given. Blank questions and questions longer
than 1,000 characters return 400. Backend failures return 502.

## Run the Java checks

For the nine portable checks and application package, run:

```bash
./mvnw package
```

These checks cover conversion response validation, ingestion failures and direct
readiness enforcement with test doubles. They do not start services or download
models. This is also the default CI build.

To run all 18 checks, stop dev mode with Ctrl+C, keep Ollama running with both
models pulled, and run:

```bash
./mvnw verify -Preader-workflow
```

The profile adds the real-service `@QuarkusTest` workflow, the no-evidence
generation check, and the same HTTP contract against the packaged JVM application
with `@QuarkusIntegrationTest`. It needs Podman, Ollama, and both models; Quarkus
starts Docling and pgvector. Ollama Dev Services is disabled because the example
uses your existing server on port 11434. The checks exercise conversion,
retrieval, source metadata, the known price, an unsupported future price, and the
input boundary. Neither test path requires Python.

## Run the package without a source tree

After the checks complete, start the two ephemeral local services:

```bash
./scripts/start-services.sh
mkdir -p target/distribution
cp -R target/quarkus-app documents scripts target/distribution/
cd target/distribution
./scripts/run-packaged.sh
```

This distribution contains no `src/` directory. In another terminal, repeat the
readiness and query commands on port 8080. The script supplies the database and
Docling URLs that a packaged application needs; Dev Services do not run in an
ordinary production launch. The script binds the application to loopback on port
8080. The database uses local demo credentials and binds to loopback on port 55432;
Docling binds to loopback on 55001. Stop the Java
process with Ctrl+C, then run `./scripts/stop-services.sh` from the distribution
directory to remove these ephemeral containers and their database contents.

While the services and application are running, inspect the stored vector count
and width with:

```bash
podman exec main-thread-rag-pgvector psql -U rag_demo -d rag -At -c \
  "SELECT json_build_object('chunks', count(*), 'min_dimension', min(vector_dims(embedding)), 'max_dimension', max(vector_dims(embedding))) FROM rag_demo_embeddings"
```

The supplied corpus produced 96 rows with minimum and maximum dimensions of 384.

## Change the corpus or configuration

`RAG_DOCUMENTS_DIRECTORY` selects a directory of lowercase `.pdf` files;
`RAG_CONVERTED_DIRECTORY` selects the Markdown output directory. Paths are resolved
from the process working directory. Every PDF in that directory is required.
A missing directory, failed conversion, wrong embedding width, or failed write
leaves the corpus unavailable. An existing directory with no PDFs reports `EMPTY`.

This demonstration owns the `rag_demo_embeddings` table and **replaces all its
rows on every application startup**, including a dev-mode reload. Use a dedicated
demo database. Startup ingestion is sequential, and a failed run may leave partial
rows in that table; HTTP queries stay blocked until a complete run succeeds.
The next successful startup replaces those rows. Do not run two instances against
the same table.

Use the same embedding model for ingestion and retrieval. `granite-embedding:30m`
returns 384-dimensional vectors here. If you change model dimensions, recreate
the dedicated table and update `rag.embedding-dimension` before re-ingesting.
The default minimum retrieval score is 0.6 and the maximum result count is three.
Calibrate those values for your own corpus; this score is not a probability that
an answer is correct. Approximate vector indexing is disabled for this small example.

The HTTP API, corpus inspection endpoints, database credentials, and unauthenticated
Docling service are intended for a local demonstration. Adding access control,
multi-user isolation, durable ingestion, and corpus permissions requires application
design beyond this walkthrough. Each question uses fresh model context; chat memory
is disabled.
