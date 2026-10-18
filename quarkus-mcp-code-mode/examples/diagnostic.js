const project = { projectDir: input.projectDir };
const discovery = quarkus.quarkus_searchTools({ ...project, query: "exception" });
if (discovery.isError) throw new Error("Discovery failed");

// The pinned diagnostic schema requires a string, including for the depth.
const diagnostic = quarkus.quarkus_callTool({
    ...project,
    toolName: "devui-exceptions_getLastException",
    toolArguments: JSON.stringify({ maxCauseDepth: "3" })
});
if (diagnostic.isError) throw new Error("Diagnostic failed");
return diagnostic;
