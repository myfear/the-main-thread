# Bob Web: a Quarkus ACP client

Bob Web is a local, ChatGPT-style interface for IBM Bob Shell. Quarkus starts one `bob acp` child process per active conversation, maps ACP session updates to browser events, and returns tool-permission choices to Bob over JSON-RPC.

The interface includes:

- A conversation list with one isolated Bob process per item
- Bob's advertised Agent, Plan, and Ask modes
- Commands and skills delivered by `available_commands_update`
- Live messages, plans, reasoning, tool calls, diffs, and usage events over SSE
- Per-tool Allow/Reject controls with fail-closed timeouts
- Workspace-root confinement and a bounded number of concurrent processes

The app uses Quarkus 3.38.3, Java 21, Quarkus REST/Jackson, and the experimental SmallRye ACP Java client `0.1.1-SNAPSHOT`.

## Prerequisites

- Java 21 or newer
- Apache Maven 3.9 or newer, used once to install the ACP snapshot
- IBM Bob Shell 2.0.2 or newer on your `PATH`, or its absolute path
- Bob access through an API key or an existing local SSO session

Review Bob's ACP license before automating its acceptance:

```bash
bob --show-license acp
```

## Install the SmallRye ACP snapshot

The ACP client has not been released to Maven Central yet. Install it into your local Maven repository:

```bash
git clone https://github.com/smallrye/smallrye-acp-client.git ../smallrye-acp-client
cd ../smallrye-acp-client
mvn -DskipTests install
cd ../bob-acp-web
```

The application depends on the core transport and schema, not the example command-line client:

```xml
<dependency>
    <groupId>io.smallrye.ai</groupId>
    <artifactId>acp-java-core</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

## Run the application

From this directory:

```bash
export BOB_PATH=/absolute/path/to/bob
export BOB_API_KEY='<your key>'
export BOB_WORKSPACE_ROOT="$PWD"

./mvnw quarkus:dev
```

Open <http://localhost:8080>, create a conversation, and select `lab` as the workspace.

`BOB_API_KEY` is read by Quarkus and passed only to the Bob child process. The application sets both the current Bob Shell variable `BOBSHELL_API_KEY` and the compatibility name `BOB_API_KEY`; it never returns the key through the HTTP API.

The default Bob arguments are:

```text
acp,--trust,--accept-license,--disable-mcp,--disable-subagents
```

Override them with a comma-separated `BOB_ARGUMENTS` value. Do not add `--auto-approve` if you want the web permission cards to mean anything.

## Run the lab

The `lab` directory contains an intentionally broken retry-budget method. Confirm the starting failure:

```bash
cd lab
./verify
```

The first failure is:

```text
java.lang.AssertionError: the budget is exhausted after three failures
```

Create a Bob Web conversation for `lab`, then send:

```text
Fix RetryBudget.java so it satisfies the README contract. Run ./verify. Change no other file.
```

Approve the file edit and `./verify` separately. The successful tool result is:

```text
RetryBudget contract verified
```

Reset the lab after the exercise:

```bash
git restore lab/src/main/java/dev/mainthread/lab/RetryBudget.java
```

## HTTP API

- `GET /api/conversations` lists active conversations.
- `POST /api/conversations` starts Bob and creates an ACP session.
- `GET /api/conversations/{id}` returns the current projection and event history.
- `GET /api/conversations/{id}/events?after={sequence}` streams SSE events and replays missed events.
- `POST /api/conversations/{id}/messages` starts a prompt turn.
- `PUT /api/conversations/{id}/mode` changes an advertised Bob mode.
- `POST /api/conversations/{id}/permissions/{toolCallId}` resolves one permission request.
- `DELETE /api/conversations/{id}/turn` sends `session/cancel`.
- `DELETE /api/conversations/{id}` closes the Bob process.

## Configuration

All properties use the `bob` prefix and can be overridden with normal Quarkus environment-variable mapping:

- `BOB_PATH` selects the executable.
- `BOB_API_KEY` supplies the credential passed to Bob.
- `BOB_ARGUMENTS` controls the ACP process arguments.
- `BOB_WORKSPACE_ROOT` limits selectable workspaces. Browser paths must be relative to this root.
- `BOB_REQUEST_TIMEOUT` limits handshake and mode operations. Default: `30s`.
- `BOB_PROMPT_TIMEOUT` limits one prompt. Default: `10m`.
- `BOB_PERMISSION_TIMEOUT` rejects an unanswered request. Default: `2m`.
- `BOB_MAX_CONVERSATIONS` bounds active Bob processes. Default: `4`.

Quarkus binds to `127.0.0.1`. Keep it that way until the application has real authentication, authorization, CSRF protection, durable audit storage, and tenant-specific workspace policies.

## Tests

Run the Quarkus test suite:

```bash
./mvnw test
```

The tests use a mocked ACP connection. They cover the REST lifecycle, workspace traversal rejection, static client delivery, exact permission choices, permission timeout rejection, and the untyped ACP text-content union.

The verified end-to-end path used Bob Shell 2.0.2 and ACP protocol version 1. It created a real session, discovered three modes and nine commands/skills, streamed plans and tool calls, paused for permissions, applied the lab patch, and returned `end_turn` after the verification passed.

## Deliberate boundaries

This is a local developer tool, not a multi-user service. Conversations and their replay buffers live in memory and disappear when Quarkus restarts. Each conversation owns a child process, so limits matter, especially if Bob starts MCP servers or subagents. The defaults disable both to keep the tutorial's process graph understandable.

Bob writes diagnostics to stderr and ACP messages to stdout. Do not merge the streams or print secrets at protocol trace level.
