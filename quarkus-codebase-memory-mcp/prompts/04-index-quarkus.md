The Quarkus source checkout is open in this workspace.

Index it with codebase-memory-mcp if it is not indexed already.

Rules for this run:

- Call `list_projects` first.
- If the Quarkus project is missing, call `index_repository` with the workspace path.
- If the project already exists, say so and do not re-index.
- Do not start the `@ConfigMapping` trace yet.
- Do not spawn subagents.

Report back with:

1. Whether the repo was already indexed or needed a fresh index
2. File, node, and edge counts if the tool returns them
3. Any error or slow step you hit
