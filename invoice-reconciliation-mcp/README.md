# invoice-reconciliation-mcp

Quarkus MCP Server demo for accounts-payable invoice reconciliation with **elicitation**, **progress**, and **cancellation**.

The sample exposes two MCP tools over Streamable HTTP at `/mcp`:

- `reconcile_invoices` — long-running analysis that may ask the client for business policy via elicitation, or accept optional inline policy arguments (clients without elicitation, such as Bob, use tutorial defaults when omitted)
- `post_reconciliation_batch` — posts matched invoices after explicit review

## Prerequisites

- JDK 25 or newer
- Docker (for PostgreSQL Dev Services during `./mvnw test` and `quarkus:dev`)

## Run in dev mode

```bash
./mvnw quarkus:dev
```

MCP endpoint: `http://127.0.0.1:8080/mcp`

## Verify

```bash
./mvnw test
```

Or run the smoke script:

```bash
./scripts/smoke.sh
```

Focused MCP tests:

```bash
./mvnw test -Dtest=InvoiceReconciliationMcpTest
./mvnw test -Dtest=ElicitationDeclinedMcpTest
./mvnw test -Dtest=ReconciliationServiceTest
```

See [article.md](article.md#mcp-client-compatibility) for MCP client compatibility (elicitation vs Bob, supplier aliases).

Expected reconciliation counts for supplier `ACME`, May 2026:

- `processed`: 32
- `matched`: 24
- `exceptions`: 8

## Optional: Wire Cursor

Copy or merge [`.mcp.json`](.mcp.json) into your project MCP config while `quarkus:dev` is running.

## Tutorial

Full walkthrough: [article.md](article.md)
