const project = { projectDir: input.projectDir };
const status = quarkus.quarkus_status(project);
const logs = quarkus.quarkus_logs({ ...project, lines: 200 });
if (status.isError || logs.isError) {
    throw new Error("An upstream operation failed");
}
return {
    status: status.content,
    errors: logs.content.filter(block => block.type === "text")
        .flatMap(block => block.text.split(/\r?\n/))
        .filter(line => line.includes("DEMO_ERROR"))
};
