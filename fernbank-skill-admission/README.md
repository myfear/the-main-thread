# Fernbank Skill Admission

Fernbank is a Quarkus MCP server that evaluates each tool against an OPA policy before the tool appears in `tools/list` or can be invoked. The policy runs in-process as WebAssembly so the synchronous MCP `ToolFilter` does not block the Vert.x event loop on a remote policy call.

The demo includes three skill manifests:

- `docs_generate`: internal, verified, and allowed for the `content` and `platform` teams
- `pptx_export`: third-party and verified, but asks for scopes outside its trust-tier allowlist
- `unsigned_status`: internal and narrow, but unsigned

Production denies invalid signatures, out-of-tier scopes, and unauthorized teams. Development keeps the team boundary but turns signature and scope violations into warnings.

## Requirements

- Java 25
- Podman
- A running Podman machine on macOS or Windows

## Build the OPA policy

The Rego source is compiled to the Wasm module loaded by Quarkus:

```bash
./scripts/build-policy.sh
```

The script uses `openpolicyagent/opa:1.17.0`, runs the Rego tests, and writes `src/main/resources/policies/skill-admission.wasm`.

## Run the application

Development uses warning mode for trust-tier scope and signature findings:

```bash
./mvnw quarkus:dev
```

The Streamable HTTP MCP endpoint is `http://localhost:8080/mcp`. The recent decision buffer is available at `http://localhost:8080/api/decisions`.

Run dev mode with production enforcement when you want to inspect the deny path interactively:

```bash
./mvnw quarkus:dev -Dfernbank.runtime-environment=prod
```

## Run the tests

```bash
./mvnw test
```

The test suite checks the Rego result model, production scope and signature denies, the development warning path, client-specific `tools/list` filtering, and a direct call to a hidden tool.

## Demo identity headers

The HTTP tests send these headers:

- `X-Fernbank-User`
- `X-Fernbank-Agent`
- `X-Fernbank-Session`
- `X-Fernbank-Team`

They keep the example self-contained. They are not an authentication mechanism. Replace them with claims from Quarkus `SecurityIdentity` and a verified OIDC access token before production use.

## Main files

- `src/main/resources/policies/skill-admission.rego`: policy source
- `src/main/resources/skills/*.json`: catalog manifests
- `OpaPolicyEvaluator`: in-process Wasm evaluation
- `OpaToolFilter`: MCP visibility and invocation gate
- `DecisionAudit`: bounded decision log with stable reason codes
- `ToolExposureTest`: Streamable HTTP proof with `McpAssured`

The project uses Quarkus 3.37.2, Quarkus MCP Server 1.13.1 from the platform BOM, OPA 1.17.0 for policy compilation, and `opa-java-wasm` 1.1.0 for evaluation.
