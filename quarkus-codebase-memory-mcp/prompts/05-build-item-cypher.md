The Quarkus graph project is `Users-meisele-Projects-quarkus`.

This is a short graph-only check. Do not open source files. Do not spawn subagents. Do not create HTML artifacts.

Run these MCP tools in order and summarize the results:

1. `query_graph`:

```cypher
MATCH (c:Class)-[:DEFINES_METHOD]->(m:Method)
WHERE c.name IN ['ConfigGenerationBuildStep', 'ConfigMappingProcessor']
RETURN c.name AS class_name, m.name AS method_name, m.file AS file_path
ORDER BY class_name, method_name
```

2. `search_graph` with name pattern `ConfigMappingBuildItem`, label `Class`, limit 10

3. `search_graph` with name pattern `ConfigMappingCreator`, label `Class`, limit 10

4. `search_graph` with name pattern `discoverConfigMappings`, label `Method`, limit 5

5. `search_graph` with name pattern `generateConfigMappings`, label `Method`, limit 5

Report back with:

1. Methods listed for each deployment class
2. Whether `discoverConfigMappings` or `generateConfigMappings` show `in_degree = 0`
3. One sentence on why that matters for Quarkus build steps vs Java call graphs
