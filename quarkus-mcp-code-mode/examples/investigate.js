const project = { projectDir: input.projectDir };
function text(result) {
    if (result.isError) throw new Error("An upstream operation failed");
    return result.content.filter(block => block.type === "text")
        .map(block => block.text).join("\n");
}

const status = text(quarkus.quarkus_status(project));
if (!status.startsWith("running")) {
    return { status, errors: [], hasException: null };
}
const errors = text(quarkus.quarkus_logs({ ...project, lines: 200 }))
    .split(/\r?\n/).filter(line => line.includes("DEMO_ERROR payment"));

let hasException = null;
if (errors.length) {
    text(quarkus.quarkus_searchTools({ ...project, query: "exception" }));
    const diagnostic = quarkus.quarkus_callTool({
        ...project,
        toolName: "devui-exceptions_getLastException",
        toolArguments: JSON.stringify({ maxCauseDepth: "3" })
    });
    hasException = JSON.parse(text(diagnostic)).hasException;
    if (typeof hasException !== "boolean") throw new Error("Unexpected diagnostic result");
}
return { status, errors, hasException };
