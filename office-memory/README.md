# Office Memory

Office Memory is a local Quarkus assistant for a fictional office coordinator. It
proposes project facts, lets you approve or reject them, and retrieves approved
facts in later conversations. You can inspect sources, replace a fact, and remove
its saved versions. The model has no approval tool.

Article: [Quarkus AI memory: approve, recall, and correct saved facts](https://www.the-main-thread.com/p/quarkus-ai-memory-approved-facts).

## Prerequisites

- Java 25.
- Podman, with its machine running on macOS or Windows.
- A running host Ollama service. This example uses `qwen3:8b` for chat and
  `nomic-embed-text` for 768-dimensional embeddings.
- Internet access for the initial Maven dependencies, container image, and model downloads.

The companion uses Quarkus 3.40.1.1 and platform-managed Quarkus LangChain4j 1.13.3.
It serves a Qute page with local CSS and JavaScript. No JavaScript build step is required.

## Run the application

From the `office-memory` directory:

```bash
ollama pull qwen3:8b
ollama pull nomic-embed-text
podman info
./mvnw quarkus:dev
```

Open [Office Memory](http://127.0.0.1:8096). If Ollama is not running, start the
Ollama application or run `ollama serve` in another terminal before starting Quarkus.

Quarkus Dev Services starts `pgvector/pgvector:0.8.2-pg17` through Podman. In dev
mode, PostgreSQL data is mounted at `.data/postgres`, relative to this directory.
Stop Quarkus with Ctrl+C and run the same command again to reopen those facts and
conversations. Do not share this directory between simultaneously running app
instances. Tests use a separate, disposable database without this volume.

The server requires `127.0.0.1`. Startup rejects a different HTTP bind address.
This is a single-user demonstration without authentication; do not expose it
through a proxy or tunnel. It does not read or send email or calendar invitations.

For a smaller chat model, download `qwen3:4b` and select it explicitly:

```bash
ollama pull qwen3:4b
./mvnw quarkus:dev -Dquarkus.langchain4j.ollama.chat-model.model-id=qwen3:4b
```

The live walkthrough was checked with `qwen3:8b`. Tool selection and wording vary
with model and prompt; the application validates every proposed operation.

## Try the offsite scenario

1. Select **Team Offsite**, enter a conversation name, and select **Create**.
2. Send: `Remember that the team offsite is in Hamburg on 19 November 2026 and we expect 24 colleagues.`
3. Inspect **Pending changes**. Expect separate location, date, and headcount
   proposals. If the model omits a detail, ask it to propose that detail separately.
   No fact is active before you select **Approve change**.
4. Approve each proposal. Create a new conversation and ask:
   `Where is the offsite, and how many people are coming? Search saved facts.`
5. Send: `Correction: the offsite location is Berlin now. Keep the date and headcount.`
   Inspect the replacement proposal, then approve it. Expand the Berlin fact's
   **Source and history** to inspect the earlier Hamburg version.
6. Create a fresh conversation and ask for the saved location, date, and headcount.
   Check both the answer and **Tool activity**. The active fact should say Berlin.
7. In a fresh conversation, send: `Please propose removing the saved attendee count for the offsite. Find the current saved fact first, then create the removal proposal. Keep the location and date.`
8. Select **Approve removal**. Ask for headcount in another fresh conversation.
   The removed number is no longer available through saved-fact retrieval.
9. Switch to **Leadership Briefing** and ask for the offsite details. The search
   must return no approved matches from Team Offsite.
10. Stop and restart Quarkus, then reopen the conversations and saved facts.

A proposal tool can be rejected. For replacement and removal, it must use an ID
retrieved by `memory_search` in that same turn. If the model stops after an error
or search, ask it to try again. The UI shows **no change proposed** when no mutation
tool succeeded. The application's proposal state is authoritative; a model can
describe a pending operation incorrectly.

## Understand the three stores

- **Conversation transcript:** full user and assistant messages for display.
  Failed user turns are retained and marked failed.
- **Context window:** a persisted LangChain4j window with at most 12 messages,
  including system and tool messages. This is a message limit, not a token budget.
  Different conversation UUIDs select different windows.
- **Saved facts:** approved atomic statements, stored as Hibernate entities with
  a vector, project, source message, status, and version history. The assistant
  receives them only after calling `memory_search`.

Search combines project and ACTIVE-status predicates inside the Hibernate
embedding-store query, before limiting results. The demo uses exact vector search.
Larger datasets can add a pgvector HNSW or IVFFlat index through a Flyway migration;
choose an index and distance operator that match the query and validate filtered
recall before changing the search strategy.

Nomic document embeddings use `search_document:` and queries use `search_query:`.
The prefixes are not stored in fact text. Startup checks the actual vector size;
changing embedding model requires a compatible schema and re-embedding existing
facts, even when another model also produces 768 dimensions.

Approval computes embeddings outside the write transaction. Short transactions
lock the project, proposal and target, check recorded versions, and commit changes with a
content-free audit event. Repeated approval returns the original result. A stale
replacement returns HTTP 409. Concurrent turns for a conversation also return 409;
the application supports one running instance.

Failed model turns reject their proposals. Startup recovers interrupted turns in
the same way. LangChain4j's deferred flush preserves the previous completed
context window after a model failure. The store retains snapshots during normal
application shutdown, when the AI service clears its process-local memory cache.

## Remove a saved fact

Removal deletes every saved revision of the fact and its vectors, clears related
proposal text and reasons, and rejects pending proposals for that fact. Audit rows
retain IDs and actions without the fact's text.

Removal preserves original conversation text and existing context windows.
This application has no conversation-deletion action. Start a fresh conversation
when checking retrieval after removal. It is not a comprehensive erasure system.

## Run tests and build

With Podman available, run:

```bash
./mvnw test
./mvnw verify -DskipITs=false
```

The deterministic tests substitute a scripted chat model and 768-dimensional fake
embeddings. They exercise the actual AI service/tool boundary and a real pgvector
database without contacting Ollama. They cover scope, history, removal, retries,
concurrent approval, stale targets, failed turns, window serialization and eviction,
and startup recovery. The packaged HTTP smoke test checks the built JVM application.

## HTTP API

All paths are relative to `http://127.0.0.1:8096`:

- `GET /api/projects`
- `POST /api/conversations` with `projectId` and `title`
- `GET /api/conversations?projectId=UUID`
- `GET /api/conversations/UUID/messages`
- `POST /api/conversations/UUID/messages` with `text` and a fresh UUID `requestId`
- `GET /api/memories?projectId=UUID`
- `GET /api/memories/UUID/history?projectId=UUID`
- `GET /api/proposals?projectId=UUID`
- `POST /api/proposals/UUID/approve` with `projectId` and `expectedVersion`
- `POST /api/proposals/UUID/reject` with `projectId` and `expectedVersion`
- `GET /api/tool-invocations?conversationId=UUID`

Successful retries with the same message `requestId` return the stored response.
Reusing that ID with different content, or retrying a failed attempt under that
ID, returns 409. Use a new ID for a new attempt. Model tools never accept project,
conversation or source-message IDs as model-generated arguments.

## Troubleshoot

- **Container connection fails:** start your Podman machine and confirm `podman info`
  succeeds. On macOS, Quarkus uses Podman's configured socket.
- **Embedding model unavailable:** check `ollama list`, the running service, and
  `quarkus.langchain4j.ollama.base-url`. The default is `http://127.0.0.1:11434`.
- **Wrong embedding dimension:** use the documented embedding model. The schema
  requires 768 dimensions, and startup reports the received size on mismatch.
- **No proposal appears:** inspect Tool activity. A reply with no tool call created
  no proposal. A rejected call also created no change; ask the assistant to retry.
- **Approval returns 409:** refresh pending changes. The source turn may be running,
  or another proposal may have replaced or removed the target. Reject a stale
  proposal and request a new one.
- **Port already occupied:** run `./mvnw quarkus:dev -Dquarkus.http.port=8097`.

Prompt/response logging is disabled by default. Tool activity stores names,
outcomes, timing, counts and source IDs, without copying tool arguments or fact text.
