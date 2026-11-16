"use strict";

const statusLabel = document.querySelector("#stream-state");
const elapsed = document.querySelector("#elapsed");
const lastCheck = document.querySelector("#last-check");
const note = document.querySelector("#library-note");
const table = document.querySelector("#recording-table");
const rows = document.querySelector("#recording-rows");
const refreshButton = document.querySelector("#refresh");

const localDate = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" });
const localTime = new Intl.DateTimeFormat(undefined, { timeStyle: "medium" });
document.querySelector("#timezone").textContent = localDate.resolvedOptions().timeZone;

let connectedSince = null;
let lastLibrary = null;
let statusRequest;
let libraryRequest;

function setText(element, text) {
    if (element.textContent !== text) element.textContent = text;
}

function clock(seconds) {
    const whole = Math.max(0, Math.floor(seconds));
    const parts = [Math.floor(whole / 3600), Math.floor(whole / 60) % 60, whole % 60];
    return parts.map(part => String(part).padStart(2, "0")).join(":");
}

function updateElapsed() {
    setText(elapsed, connectedSince === null ? "—" : clock((Date.now() - connectedSince) / 1000));
}

async function getJson(path) {
    const response = await fetch(path, { cache: "no-store", headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
}

async function loadStatus() {
    try {
        const status = await getJson("/api/atem/status");
        connectedSince = status.online ? Date.parse(status.connectedSince) : null;
        statusLabel.dataset.state = status.online ? "online" : "idle";
        setText(statusLabel, status.online ? "Stream connected" : "Waiting for stream");
        const checked = new Date();
        lastCheck.dateTime = checked.toISOString();
        setText(lastCheck, localTime.format(checked));
    } catch {
        connectedSince = null;
        statusLabel.dataset.state = "error";
        setText(statusLabel, "Recorder unavailable");
    }
    updateElapsed();
}

function recordingRow(range) {
    const row = document.createElement("tr");
    row.setAttribute("role", "row");
    const start = row.insertCell();
    const duration = row.insertCell();
    const download = row.insertCell();
    for (const cell of row.cells) cell.setAttribute("role", "cell");

    const timestamp = document.createElement("time");
    timestamp.dateTime = range.start;
    timestamp.textContent = localDate.format(new Date(range.start));
    start.append(timestamp);
    duration.textContent = clock(range.duration);

    const link = document.createElement("a");
    link.className = "control";
    link.href = range.downloadUrl;
    link.dataset.start = range.start;
    link.textContent = "Download MP4";
    link.setAttribute("aria-label", `Download MP4 from ${timestamp.textContent}`);
    download.append(link);
    return row;
}

async function loadLibrary() {
    try {
        const library = await getJson("/api/atem/recordings");
        const snapshot = JSON.stringify(library);
        if (lastLibrary !== snapshot) {
            const focusedStart = rows.contains(document.activeElement) ? document.activeElement.dataset.start : null;
            rows.replaceChildren(...library.map(recordingRow));
            if (focusedStart) {
                const link = rows.querySelector(`a[data-start="${CSS.escape(focusedStart)}"]`);
                (link ?? refreshButton).focus({ preventScroll: true });
            }
            lastLibrary = snapshot;
        }
        table.hidden = library.length === 0;
        note.hidden = library.length > 0;
        note.dataset.error = "false";
        setText(note, library.length ? "" : "No recordings yet. Start a stream to create one.");
    } catch {
        note.hidden = false;
        note.dataset.error = "true";
        setText(note, table.hidden
            ? "Could not load recordings. Check MediaMTX and refresh."
            : "Could not refresh recordings. Showing the last successful list.");
    }
}

function refreshStatus() {
    statusRequest ??= loadStatus().finally(() => { statusRequest = undefined; });
    return statusRequest;
}

function refreshLibrary() {
    libraryRequest ??= loadLibrary().finally(() => { libraryRequest = undefined; });
    return libraryRequest;
}

refreshButton.addEventListener("click", async () => {
    refreshButton.disabled = true;
    setText(refreshButton, "Refreshing…");
    await Promise.all([refreshStatus(), refreshLibrary()]);
    setText(refreshButton, "Refresh");
    refreshButton.disabled = false;
});

refreshStatus();
refreshLibrary();
setInterval(refreshStatus, 3000);
setInterval(refreshLibrary, 10000);
setInterval(updateElapsed, 1000);
