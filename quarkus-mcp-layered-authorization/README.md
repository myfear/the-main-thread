# Quarkus MCP Layered Authorization

This standalone companion application implements the article **Quarkus MCP 2.0: Layered Authorization for Stateless Tool Calls**. The application keeps the Fernbank example domain from the article, but it lives in its own runnable Maven module.

It separates four controls:

- Quarkus MCP Server validates stateless `2026-07-28` request metadata and the `Mcp-Method` and `Mcp-Name` headers.
- Quarkus OIDC authenticates the caller and checks the access-token audience.
- An in-process OPA Wasm policy filters tool discovery and direct invocation through `ToolFilter`.
- A Beta3 `ToolInputGuardrail` authorizes the `destinationTeam` argument before `docs_generate` runs.

The project uses Quarkus 3.37.3, Quarkus MCP Server 2.0.0.Beta3, Java 21, OPA 1.17.0 for policy compilation, and `opa-java-wasm` 1.1.0 for evaluation.

## Requirements

- Java 21
- Podman
- A running Podman machine on macOS or Windows

## Build and test the OPA policy

```bash
./scripts/build-policy.sh
```

The script runs five Rego tests in `openpolicyagent/opa:1.17.0` and writes `src/main/resources/policies/skill-admission.wasm`.

## Run the Java tests

```bash
./mvnw test
```

The ten tests cover:

- unauthenticated MCP requests
- stateless header/body name mismatches
- production OPA scope and signature decisions
- development warning behavior
- filtered `tools/list` results
- direct calls to a denied tool
- allowed and denied `destinationTeam` arguments
- fail-closed filtering when no authenticated request context is available

The MCP tests use `McpAssured.newStreamableClient().setStateless()` and `@TestSecurity`. They do not require an external identity provider.

`ToolFilterRequestContextTest` calls the filter directly without an authenticated request. This matters because Quarkus MCP Server 2.0.0.Beta3 logs and ignores runtime exceptions from a `ToolFilter`. The filter therefore activates its own request context, rejects an anonymous identity, and catches unexpected authorization failures before they can escape to the extension.

## Run with OIDC

The production profile expects an OIDC issuer URL:

```bash
export FERNBANK_OIDC_AUTH_SERVER_URL=https://id.example.com/realms/fernbank
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Access tokens must have the `fernbank-mcp` audience. Team membership is represented by the `content` or `platform` role. The `mcp-auditor` role protects `GET /api/decisions`.

The Streamable HTTP endpoint is `http://localhost:8080/mcp`. The default dev profile disables the OIDC tenant but keeps the MCP endpoint authenticated, so unauthenticated manual requests return 401 by design.

The CORS filter allows the local MCP Inspector origin (`http://localhost:6274`) by default. Set `FERNBANK_MCP_CORS_ORIGINS` to the exact browser origins used by your deployment.

## Main files

- `src/main/resources/policies/skill-admission.rego`: OPA source policy
- `src/main/resources/policies/skill-admission.wasm`: compiled policy artifact
- `src/main/resources/skills/*.json`: publisher and capability manifests
- `src/main/java/com/themainthread/fernbank/OpaToolFilter.java`: per-request tool discovery and invocation policy
- `src/main/java/com/themainthread/fernbank/DestinationTeamGuardrail.java`: argument-level team authorization
- `src/main/java/com/themainthread/fernbank/DecisionAudit.java`: bounded decisions with request ID and transient-connection state
- `src/test/java/com/themainthread/fernbank/ProtocolBoundaryTest.java`: authentication and stateless protocol checks
- `src/test/java/com/themainthread/fernbank/ToolExposureTest.java`: OPA visibility and direct-call checks
- `src/test/java/com/themainthread/fernbank/ToolArgumentAuthorizationTest.java`: business-argument checks
- `src/test/java/com/themainthread/fernbank/ToolFilterRequestContextTest.java`: fail-closed request-context regression

Quarkus MCP Server 2.0.0.Beta3 is a pre-release. Its APIs and the `2026-07-28` protocol may change before a final release.
