const logs = quarkus.quarkus_logs({ projectDir: input.projectDir, lines: 200 });
if (logs.isError) throw new Error("Log retrieval failed");

const lines = logs.content.filter(block => block.type === "text")
    .flatMap(block => block.text.split(/\r?\n/));
const unique = new Map();
for (const line of lines) {
    if (!line.startsWith("R ")) continue;
    const event = JSON.parse(line.slice(2));
    unique.set(event.eventId, event);
}
if (unique.size === 0) throw new Error("No request events in this log window");

const latest = new Map();
for (const event of unique.values()) {
    const previous = latest.get(event.requestId);
    if (!previous || event.attempt > previous.attempt) {
        latest.set(event.requestId, event);
    }
}

const groups = new Map();
for (const event of latest.values()) {
    if (!groups.has(event.endpoint)) groups.set(event.endpoint, []);
    groups.get(event.endpoint).push(event);
}
const endpoints = [...groups.keys()].sort().map(endpoint => {
    const requests = groups.get(endpoint);
    const durations = requests.filter(event => event.outcome === "ok")
        .map(event => event.durationMs).sort((a, b) => a - b);
    return {
        endpoint,
        requestCount: requests.length,
        finalFailureCount: requests.filter(event => event.outcome === "error").length,
        p95DurationMs: durations.length
            ? durations[Math.ceil(0.95 * durations.length) - 1] : null
    };
});
return {
    endpoints,
    failedAfterRetry: [...latest.values()]
        .filter(event => event.attempt > 1 && event.outcome === "error")
        .map(event => event.requestId).sort()
};
