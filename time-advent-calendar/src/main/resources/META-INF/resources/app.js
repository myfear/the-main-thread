"use strict";

const $ = (id) => document.getElementById(id);
const storageKey = "main-thread-time-calendar-v1";
let settings = { at: "2026-12-24T12:00:00Z", zone: "Europe/Berlin", mode: "explore" };
let selected = 7;
let variant = "default";
let calendar;
let calendarPending = false;
let controller;
let generation = 0;
let visited = new Set();

try {
    const saved = JSON.parse(localStorage.getItem(storageKey));
    if (saved && typeof saved.at === "string" && typeof saved.zone === "string"
            && ["explore", "replay"].includes(saved.mode)) {
        settings = { at: saved.at, zone: saved.zone, mode: saved.mode };
    }
} catch (_) {
    // Storage is optional; the lab also works in a private browser.
}

function fillControls() {
    $("instant").value = settings.at.slice(0, 19);
    if (![...$("zone").options].some((option) => option.value === settings.zone)) {
        settings.zone = "Europe/Berlin";
    }
    $("zone").value = settings.zone;
    $("mode").value = settings.mode;
}

function save() {
    try { localStorage.setItem(storageKey, JSON.stringify(settings)); } catch (_) { /* optional */ }
}

function beginRequest() {
    controller?.abort();
    controller = new AbortController();
    generation += 1;
    $("clock-error").hidden = true;
    $("run-error").hidden = true;
    $("experiment").setAttribute("aria-busy", "true");
    $("run").disabled = true;
    return { signal: controller.signal, version: generation };
}

async function request(url, options) {
    const response = await fetch(url, options);
    let body;
    try { body = await response.json(); } catch (_) { throw new Error("The server returned an unreadable response. Check the application log and retry."); }
    if (!response.ok) throw new Error(body.message || `Request failed (${response.status}). Retry after checking the application.`);
    return body;
}

function renderCalendar() {
    const focusedDoor = document.activeElement?.dataset.door;
    const clock = calendar.clock;
    const available = calendar.doors.filter((door) => door.available).length;
    $("clock-status").textContent = `${clock.simulated ? "Simulated" : "Current"} instant ${clock.instant} · ${clock.localDate} in ${clock.zone}`;
    $("available-count").textContent = `${available} / 24 available`;
    $("doors").replaceChildren(...calendar.doors.map((door) => {
        const button = document.createElement("button");
        button.type = "button";
        button.dataset.door = String(door.id);
        button.className = `door${door.deep ? " deep" : ""}${door.available ? "" : " locked"}${visited.has(door.id) ? " visited" : ""}`;
        button.setAttribute("aria-current", String(door.id === selected));
        button.setAttribute("aria-label", `Door ${door.id}: ${door.title}. ${door.available ? "Available" : `Locked until ${door.unlockAt}`}${door.deep ? ". In the article" : ""}`);
        button.title = door.title;
        const marker = document.createElement("span");
        marker.className = "marker";
        marker.textContent = door.deep ? "Article" : door.database ? "PG" : "";
        const number = document.createElement("span");
        number.className = "number";
        number.textContent = String(door.id).padStart(2, "0");
        const state = document.createElement("span");
        state.className = "state";
        state.textContent = door.available ? (visited.has(door.id) ? "Explored" : "Open door") : "Locked";
        button.append(marker, number, state);
        button.addEventListener("click", () => {
            selected = door.id;
            variant = "default";
            renderCalendar();
            runDoor(true);
        });
        return button;
    }));
    if (focusedDoor) document.querySelector(`[data-door="${focusedDoor}"]`)?.focus({ preventScroll: true });
}

function showDoorTitle(door) {
    $("door-number").textContent = String(door.id).padStart(2, "0");
    $("experiment-heading").textContent = door.title;
    $("experiment-kind").textContent = door.database ? "Real PostgreSQL experiment" : "Java time experiment";
}

function showResult(view) {
    const lesson = view.lesson;
    showDoorTitle(lesson);
    $("summary").textContent = lesson.summary;
    $("challenge").textContent = lesson.challenge;
    $("challenge").hidden = false;
    $("variant").replaceChildren();
    for (const [value, label] of [["default", lesson.defaultLabel], ["alternative", lesson.alternativeLabel]]) {
        if (!label) continue;
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        $("variant").append(option);
    }
    $("variant").value = view.variant;
    $("variant").disabled = !lesson.alternativeLabel;
    $("run-controls").hidden = false;
    $("observations").replaceChildren(...view.result.observations.map((observation) => {
        const row = document.createElement("div");
        row.className = "observation";
        const label = document.createElement("dt");
        label.textContent = observation.label;
        const value = document.createElement("dd");
        value.textContent = observation.value;
        row.append(label, value);
        return row;
    }));
    $("takeaway").textContent = view.result.takeaway;
    $("code").textContent = lesson.code;
    $("reference").href = lesson.reference;
    $("reference").textContent = lesson.database ? "Read the database or scheduling reference ↗" : "Read the reference ↗";
    $("output").hidden = false;
    $("run-status").textContent = `Completed · ${lesson.database ? "PostgreSQL and Java" : "Java 25"} · ${view.variant === "alternative" ? lesson.alternativeLabel : lesson.defaultLabel}`;
    visited.add(lesson.id);
    renderCalendar();
}

async function runDoor(moveFocus = false, transaction = null) {
    if (calendarPending) return;
    const task = transaction || beginRequest();
    const door = calendar?.doors.find((entry) => entry.id === selected);
    if (!door) return;
    showDoorTitle(door);
    $("output").hidden = true;
    $("challenge").hidden = true;
    $("run-controls").hidden = true;
    $("code-details").open = false;
    $("summary").textContent = door.available ? "Running this scenario…" : `This door opens at ${door.unlockAt} in Replay mode. Advance the clock or switch to Explore.`;
    $("run-status").textContent = door.available ? "Running the experiment…" : "Locked by the server’s calendar rule.";
    if (moveFocus && matchMedia("(max-width: 720px)").matches) {
        $("experiment").focus({ preventScroll: true });
        $("experiment").scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    }
    try {
        if (!door.available) return;
        const view = await request(`/api/doors/${selected}/run`, {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ...settings, variant }), signal: task.signal
        });
        if (task.version !== generation) return;
        showResult(view);
    } catch (error) {
        if (error.name === "AbortError" || task.version !== generation) return;
        $("summary").textContent = "The experiment did not finish.";
        $("run-error").textContent = error.message;
        $("run-error").hidden = false;
        $("run-status").textContent = "Choose this door again to retry.";
    } finally {
        if (task.version === generation) {
            $("experiment").setAttribute("aria-busy", "false");
            $("run").disabled = false;
        }
    }
}

async function loadCalendar() {
    const task = beginRequest();
    calendarPending = true;
    $("advance").disabled = true;
    $("run-controls").hidden = true;
    $("challenge").hidden = true;
    $("clock-status").textContent = "Applying the calendar clock…";
    $("run-status").textContent = "Waiting for the applied calendar…";
    $("output").hidden = true;
    $("doors").querySelectorAll("button").forEach((button) => { button.disabled = true; });
    try {
        const view = await request(`/api/calendar?${new URLSearchParams(settings)}`, { signal: task.signal });
        if (task.version !== generation) return;
        calendar = view;
        calendarPending = false;
        $("advance").disabled = false;
        save();
        renderCalendar();
        await runDoor(false, task);
    } catch (error) {
        if (error.name === "AbortError" || task.version !== generation) return;
        $("clock-error").textContent = error.message;
        $("clock-error").hidden = false;
        $("clock-status").textContent = "Clock not applied. Correct the settings and try again.";
        calendar = null;
        calendarPending = false;
        $("advance").disabled = false;
        $("available-count").textContent = "";
        $("doors").replaceChildren();
        $("run-status").textContent = "";
        $("run-controls").hidden = true;
        $("experiment").setAttribute("aria-busy", "false");
        $("run").disabled = false;
    }
}

$("clock-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const value = $("instant").value;
    const at = new Date(`${value}Z`);
    if (Number.isNaN(at.getTime())) return;
    settings = { at: at.toISOString(), zone: $("zone").value, mode: $("mode").value };
    variant = "default";
    loadCalendar();
});

document.querySelectorAll("[data-preset]").forEach((button) => button.addEventListener("click", () => {
    const preset = button.dataset.preset;
    const instants = { start: "2026-12-01T12:00:00Z", boundary: "2026-12-23T22:59:59Z", eve: "2026-12-24T12:00:00Z" };
    settings.at = preset === "now" ? new Date().toISOString() : instants[preset];
    if (preset === "start" || preset === "boundary") settings.mode = "replay";
    if (preset === "boundary") settings.zone = "Europe/Berlin";
    fillControls();
    loadCalendar();
}));

$("advance").addEventListener("click", async () => {
    const task = beginRequest();
    try {
        const result = await request("/api/clock/advance", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify(settings), signal: task.signal
        });
        if (task.version !== generation) return;
        settings.at = result.instant;
        fillControls();
        await loadCalendar();
    } catch (error) {
        if (error.name === "AbortError" || task.version !== generation) return;
        $("clock-error").textContent = error.message;
        $("clock-error").hidden = false;
        $("experiment").setAttribute("aria-busy", "false");
        $("run").disabled = false;
    }
});

$("run").addEventListener("click", () => { variant = $("variant").value; runDoor(); });
$("variant").addEventListener("change", () => { variant = $("variant").value; runDoor(); });
fillControls();
loadCalendar();
