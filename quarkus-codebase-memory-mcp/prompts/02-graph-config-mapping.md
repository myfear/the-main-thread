The Quarkus source tree is indexed in codebase-memory-mcp.

Project name: `Users-meisele-Projects-quarkus`

Trace how `@ConfigMapping` classes move from deployment scanning and generated mapping classes to synthetic bean registration and runtime creation.

Rules for this run:

- Use codebase-memory-mcp MCP tools only for the first 15 tool calls: `search_graph`, `query_graph`, `get_code_snippet`, and `trace_path` if a plain call chain is the right question.
- Do not spawn subagents.
- Do not create HTML artifacts.
- Do not load skills.
- Use `grep` or `read_file` only after the graph narrows the target (maximum one `read_file` for confirmation).
- Quarkus build steps connect through build items, not normal Java call chains. Say that explicitly in your answer.

Required MCP sequence:

1. `list_projects` — confirm project name
2. `query_graph` with this Cypher:

```cypher
MATCH (c:Class)-[:DEFINES_METHOD]->(m:Method)
WHERE c.name IN ['ConfigGenerationBuildStep', 'ConfigMappingProcessor']
RETURN c.name AS class_name, m.name AS method_name, m.file AS file_path
ORDER BY class_name, method_name
```

3. `search_graph` for `ConfigMappingCreator` and `ConfigMappingBuildItem`
4. `get_code_snippet` for the methods you need — not whole files
5. Optional: `trace_path` on `ConfigMappingCreator` only if you explain why a call chain applies

Report back in plain markdown (no HTML):

1. MCP tools you called, in order
2. Files and methods the graph surfaced
3. Which steps are build-time and which are runtime
4. Any graph edge you distrust and why (especially `in_degree = 0` on build steps)

If `get_code_snippet` fails with "symbol not found", run `search_graph` again and use the `qualified_name` from the result, not a Java package name you guess.
