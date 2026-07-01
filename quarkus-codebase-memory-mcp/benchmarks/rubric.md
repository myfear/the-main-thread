# Benchmark rubric

Use the same task for the baseline path (shell + `rg` + file reads) and the graph path (`search_graph`, `query_graph`, `get_code_snippet`, `trace_path`).

Score each path on:

1. **Right files** — Did the path surface the deployment, arc deployment, and runtime classes that matter for `@ConfigMapping`?
2. **Phase boundaries** — Did it separate build-time discovery/generation from synthetic bean registration and runtime materialization?
3. **Missed edges** — Did it claim a plain `CALLS` chain where Quarkus actually uses build items, recorders, or synthetic beans?
4. **Residual reading** — How many full file reads were still required after the first structured result?

Expected anchor files in current Quarkus (`main` as of July 2026):

- `core/deployment/src/main/java/io/quarkus/deployment/steps/ConfigGenerationBuildStep.java`
- `core/deployment/src/main/java/io/quarkus/deployment/configuration/ConfigMappingUtils.java`
- `extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/ConfigMappingProcessor.java`
- `extensions/arc/runtime/src/main/java/io/quarkus/arc/runtime/ConfigMappingCreator.java`
- `core/runtime/src/main/java/io/quarkus/runtime/configuration/AbstractConfigBuilder.java`

The baseline path should need more hops and more token-heavy file reads. The graph path should narrow the search space faster, but it should not pretend build-step scheduling is a normal inbound call graph.
