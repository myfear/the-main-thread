const state = {
    conversations: [],
    current: null,
    eventSource: null,
    lastSequence: 0,
    commands: [],
    agentMessage: null,
    thoughtCard: null,
    tools: new Map(),
    permissions: new Map()
};

const elements = Object.fromEntries([
    "sidebar", "mobile-menu", "new-chat", "empty-new-chat", "conversation-list", "conversation-title",
    "workspace-label", "mode-select", "commands-button", "command-count", "empty-state", "conversation",
    "timeline", "prompt", "send-button", "stop-button", "command-drawer", "close-commands", "command-search",
    "command-list", "scrim", "new-conversation-dialog", "new-conversation-form", "workspace-input",
    "create-button", "toast"
].map(id => [id, document.getElementById(id)]));

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {"Content-Type": "application/json", ...(options.headers || {})}
    });
    if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
            message = (await response.json()).message || message;
        } catch (_) {
            // The fallback already contains a useful HTTP error.
        }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

async function loadConversations() {
    state.conversations = await api("/api/conversations");
    renderConversationList();
}

function renderConversationList() {
    elements["conversation-list"].replaceChildren();
    for (const conversation of state.conversations) {
        const item = el("div", "conversation-item");
        item.classList.toggle("active", state.current?.id === conversation.id);
        item.tabIndex = 0;
        item.addEventListener("click", () => selectConversation(conversation.id));
        item.addEventListener("keydown", event => {
            if (event.key === "Enter") selectConversation(conversation.id);
        });

        const copy = el("div", "conversation-copy");
        copy.append(el("strong", null, conversation.title));
        copy.append(el("small", null, `${conversation.currentMode || "connecting"} · ${shortPath(conversation.workspace)}`));
        const close = el("button", "conversation-close", "×");
        close.type = "button";
        close.title = "Close Bob process";
        close.addEventListener("click", async event => {
            event.stopPropagation();
            await closeConversation(conversation.id);
        });
        item.append(copy, close);
        elements["conversation-list"].append(item);
    }
}

async function selectConversation(id) {
    try {
        const view = await api(`/api/conversations/${encodeURIComponent(id)}`);
        disconnectEvents();
        resetTimeline();
        state.current = view;
        state.commands = view.commands || [];
        renderConversationHeader();
        for (const event of view.events || []) {
            applyEvent(event, false);
        }
        elements["empty-state"].hidden = true;
        elements.conversation.hidden = false;
        updateComposer(view.status);
        renderConversationList();
        renderCommands();
        connectEvents();
        scrollToBottom();
        elements.sidebar.classList.remove("open");
    } catch (error) {
        showError(error);
    }
}

function renderConversationHeader() {
    const view = state.current;
    elements["conversation-title"].textContent = view.title;
    elements["workspace-label"].textContent = `${view.agentName} ${view.agentVersion} · ${view.workspace}`;
    elements["mode-select"].replaceChildren();
    for (const mode of view.modes || []) {
        const option = document.createElement("option");
        option.value = mode.id;
        option.textContent = mode.name || mode.id;
        option.title = mode.description || "";
        option.selected = mode.id === view.currentMode;
        elements["mode-select"].append(option);
    }
    elements["mode-select"].disabled = !(view.modes || []).length;
    elements["commands-button"].disabled = false;
    elements["command-count"].textContent = state.commands.length;
}

function connectEvents() {
    if (!state.current) return;
    state.eventSource = new EventSource(`/api/conversations/${encodeURIComponent(state.current.id)}/events?after=${state.lastSequence}`);
    state.eventSource.onmessage = message => {
        const event = JSON.parse(message.data);
        applyEvent(event, true);
    };
    state.eventSource.onerror = () => {
        if (state.current) elements["workspace-label"].textContent = `Reconnecting · ${state.current.workspace}`;
    };
    state.eventSource.onopen = () => renderConversationHeader();
}

function disconnectEvents() {
    state.eventSource?.close();
    state.eventSource = null;
}

function applyEvent(event, live) {
    if (event.sequence <= state.lastSequence) return;
    state.lastSequence = event.sequence;
    const data = event.data || {};

    switch (event.type) {
        case "user_message":
            state.agentMessage = null;
            state.thoughtCard = null;
            addMessage("user", "You", data.text, event.at);
            setStatus("running");
            break;
        case "agent_message_chunk":
            appendAgentText(data.text || "", event.at);
            break;
        case "thought_chunk":
            appendThought(data.text || "");
            break;
        case "plan":
            renderPlan(data.entries || []);
            break;
        case "tool_call":
        case "tool_update":
            renderTool(data);
            break;
        case "permission_requested":
            renderPermission(data);
            break;
        case "permission_decided":
            settlePermission(data);
            break;
        case "commands":
            state.commands = data.commands || [];
            renderCommands();
            break;
        case "mode":
            state.current.currentMode = data.currentMode;
            elements["mode-select"].value = data.currentMode;
            break;
        case "session_info":
            if (data.title) {
                state.current.title = data.title;
                elements["conversation-title"].textContent = data.title;
            }
            break;
        case "turn_complete":
            addSystemLine(`Turn finished: ${data.stopReason || "end_turn"}`);
            setStatus("ready");
            refreshSidebarSoon();
            break;
        case "cancel_requested":
            addSystemLine("Cancellation requested");
            break;
        case "error":
            addSystemLine(data.message || "Bob reported an error", true);
            setStatus("ready");
            break;
        default:
            break;
    }

    if (live) scrollToBottom();
}

function addMessage(role, name, text, timestamp) {
    const message = el("article", `message ${role}`);
    const avatar = role === "user" ? el("div", "avatar", "Y") : document.createElement("img");
    if (role !== "user") {
        avatar.className = "avatar bob-avatar";
        avatar.src = "/Bob.svg";
        avatar.alt = "";
    }
    message.append(avatar);
    const copy = el("div", "message-copy");
    const header = document.createElement("header");
    header.append(el("strong", null, name), el("time", null, formatTime(timestamp)));
    copy.append(header, el("pre", "message-text", text || ""));
    message.append(copy);
    elements.timeline.append(message);
    return message;
}

function appendAgentText(text, timestamp) {
    if (!state.agentMessage) {
        state.agentMessage = addMessage("agent", state.current?.agentName || "Bob", "", timestamp);
    }
    state.agentMessage.querySelector(".message-text").textContent += text;
}

function appendThought(text) {
    if (!state.thoughtCard) {
        const card = eventCard("Reasoning trace", "thought");
        card.classList.add("thought-card");
        const copy = el("pre", "thought-copy", "");
        card.append(copy);
        elements.timeline.append(card);
        state.thoughtCard = card;
    }
    state.thoughtCard.querySelector("pre").textContent += text;
}

function renderPlan(entries) {
    const previous = document.getElementById("current-plan");
    previous?.remove();
    const card = eventCard("Bob's plan", "plan");
    card.id = "current-plan";
    const list = el("ol", "plan-list");
    for (const entry of entries) {
        const item = document.createElement("li");
        item.append(
            el("span", null, planMark(entry.status)),
            el("span", null, entry.content || "Untitled step"),
            el("span", "event-badge", enumText(entry.status))
        );
        list.append(item);
    }
    card.append(list);
    elements.timeline.append(card);
}

function renderTool(data) {
    let card = state.tools.get(data.toolCallId);
    if (!card) {
        card = eventCard(data.title || "Tool call", enumText(data.kind));
        card.dataset.toolCallId = data.toolCallId;
        card.append(el("pre", null, ""));
        elements.timeline.append(card);
        state.tools.set(data.toolCallId, card);
    }
    card.querySelector("h3").textContent = data.title || "Tool call";
    card.querySelector(".event-badge").textContent = enumText(data.status || data.kind);
    const details = {
        input: data.rawInput,
        output: data.rawOutput,
        content: data.content
    };
    card.querySelector("pre").textContent = JSON.stringify(details, null, 2);
}

function renderPermission(data) {
    const card = eventCard(data.title || "Permission required", enumText(data.kind));
    card.classList.add("permission-card");
    card.dataset.permissionId = data.toolCallId;
    card.append(el("pre", null, JSON.stringify({input: data.rawInput, content: data.content}, null, 2)));
    const actions = el("div", "permission-actions");
    for (const option of data.options || []) {
        const button = el("button", null, option.name || enumText(option.kind));
        button.type = "button";
        button.dataset.kind = option.kind;
        button.addEventListener("click", () => decidePermission(data.toolCallId, option.id));
        actions.append(button);
    }
    card.append(actions);
    elements.timeline.append(card);
    state.permissions.set(data.toolCallId, card);
}

function settlePermission(data) {
    const card = state.permissions.get(data.toolCallId);
    if (!card) return;
    card.querySelectorAll("button").forEach(button => button.disabled = true);
    const suffix = data.timedOut ? " (timed out; rejected)" : "";
    card.querySelector(".event-badge").textContent = `decided${suffix}`;
}

async function decidePermission(toolCallId, optionId) {
    try {
        await api(`/api/conversations/${encodeURIComponent(state.current.id)}/permissions/${encodeURIComponent(toolCallId)}`, {
            method: "POST",
            body: JSON.stringify({optionId})
        });
    } catch (error) {
        showError(error);
    }
}

function renderCommands() {
    const filter = elements["command-search"].value.trim().toLowerCase();
    elements["command-list"].replaceChildren();
    const commands = state.commands.filter(command =>
        command.name.toLowerCase().includes(filter) || (command.description || "").toLowerCase().includes(filter));
    for (const command of commands) {
        const entry = el("article", "command-entry");
        entry.append(el("code", null, `/${command.name}`));
        entry.append(el("p", null, command.description || "No description advertised"));
        entry.addEventListener("click", () => {
            elements.prompt.value = `/${command.name} `;
            resizePrompt();
            closeCommandDrawer();
            elements.prompt.focus();
        });
        elements["command-list"].append(entry);
    }
    if (!commands.length) {
        elements["command-list"].append(el("p", "system-line", "Bob has not advertised any matching commands."));
    }
    elements["command-count"].textContent = state.commands.length;
}

function eventCard(title, badge) {
    const card = el("article", "event-card");
    const header = document.createElement("header");
    header.append(el("h3", null, title), el("span", "event-badge", badge || "event"));
    card.append(header);
    return card;
}

function addSystemLine(text, isError = false) {
    const line = el("p", `system-line${isError ? " error" : ""}`, text);
    elements.timeline.append(line);
}

async function sendPrompt() {
    const prompt = elements.prompt.value.trim();
    if (!prompt || !state.current) return;
    elements.prompt.value = "";
    resizePrompt();
    try {
        await api(`/api/conversations/${encodeURIComponent(state.current.id)}/messages`, {
            method: "POST",
            body: JSON.stringify({prompt})
        });
        setStatus("running");
    } catch (error) {
        elements.prompt.value = prompt;
        resizePrompt();
        showError(error);
    }
}

async function createConversation(event) {
    event.preventDefault();
    const workspace = elements["workspace-input"].value.trim();
    if (!workspace) return;
    elements["create-button"].disabled = true;
    elements["create-button"].textContent = "Connecting…";
    try {
        const view = await api("/api/conversations", {
            method: "POST",
            body: JSON.stringify({workspace})
        });
        elements["new-conversation-dialog"].close();
        await loadConversations();
        await selectConversation(view.id);
    } catch (error) {
        showError(error);
    } finally {
        elements["create-button"].disabled = false;
        elements["create-button"].textContent = "Connect Bob";
    }
}

async function closeConversation(id) {
    try {
        await api(`/api/conversations/${encodeURIComponent(id)}`, {method: "DELETE"});
        if (state.current?.id === id) {
            disconnectEvents();
            state.current = null;
            resetTimeline();
            elements.conversation.hidden = true;
            elements["empty-state"].hidden = false;
            elements["conversation-title"].textContent = "Bob Web";
            elements["workspace-label"].textContent = "Start a conversation to connect Bob";
        }
        await loadConversations();
    } catch (error) {
        showError(error);
    }
}

function setStatus(status) {
    if (!state.current) return;
    state.current.status = status;
    updateComposer(status);
}

function updateComposer(status) {
    const running = status === "running";
    elements.prompt.disabled = status !== "ready";
    elements["send-button"].disabled = status !== "ready";
    elements["stop-button"].hidden = !running;
    elements["mode-select"].disabled = running || !(state.current?.modes || []).length;
}

function resetTimeline() {
    elements.timeline.replaceChildren();
    state.lastSequence = 0;
    state.agentMessage = null;
    state.thoughtCard = null;
    state.tools.clear();
    state.permissions.clear();
}

function openCommandDrawer() {
    elements["command-drawer"].classList.add("open");
    elements["command-drawer"].setAttribute("aria-hidden", "false");
    elements.scrim.hidden = false;
    elements["command-search"].focus();
}

function closeCommandDrawer() {
    elements["command-drawer"].classList.remove("open");
    elements["command-drawer"].setAttribute("aria-hidden", "true");
    elements.scrim.hidden = true;
}

function showError(error) {
    elements.toast.textContent = error.message || String(error);
    elements.toast.hidden = false;
    window.clearTimeout(showError.timeout);
    showError.timeout = window.setTimeout(() => elements.toast.hidden = true, 6500);
}

function refreshSidebarSoon() {
    window.setTimeout(() => loadConversations().catch(showError), 150);
}

function resizePrompt() {
    elements.prompt.style.height = "auto";
    elements.prompt.style.height = `${Math.min(elements.prompt.scrollHeight, 180)}px`;
}

function scrollToBottom() {
    requestAnimationFrame(() => elements.timeline.scrollTo({top: elements.timeline.scrollHeight, behavior: "smooth"}));
}

function shortPath(path) {
    const parts = path.split("/").filter(Boolean);
    return parts.slice(-2).join("/") || ".";
}

function formatTime(value) {
    return value ? new Date(value).toLocaleTimeString([], {hour: "2-digit", minute: "2-digit"}) : "";
}

function enumText(value) {
    if (!value) return "unknown";
    return String(value).toLowerCase().replaceAll("_", " ");
}

function planMark(status) {
    const normalized = enumText(status);
    if (normalized.includes("completed")) return "✓";
    if (normalized.includes("progress")) return "→";
    return "○";
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function showNewConversation() {
    elements["new-conversation-dialog"].showModal();
    elements["workspace-input"].focus();
    elements["workspace-input"].select();
}

elements["new-chat"].addEventListener("click", showNewConversation);
elements["empty-new-chat"].addEventListener("click", showNewConversation);
elements["new-conversation-form"].addEventListener("submit", createConversation);
elements["send-button"].addEventListener("click", sendPrompt);
elements.prompt.addEventListener("input", resizePrompt);
elements.prompt.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        sendPrompt();
    }
});
elements["stop-button"].addEventListener("click", async () => {
    try {
        await api(`/api/conversations/${encodeURIComponent(state.current.id)}/turn`, {method: "DELETE"});
    } catch (error) {
        showError(error);
    }
});
elements["mode-select"].addEventListener("change", async event => {
    const previous = state.current.currentMode;
    try {
        await api(`/api/conversations/${encodeURIComponent(state.current.id)}/mode`, {
            method: "PUT",
            body: JSON.stringify({modeId: event.target.value})
        });
    } catch (error) {
        event.target.value = previous;
        showError(error);
    }
});
elements["commands-button"].addEventListener("click", openCommandDrawer);
elements["close-commands"].addEventListener("click", closeCommandDrawer);
elements.scrim.addEventListener("click", closeCommandDrawer);
elements["command-search"].addEventListener("input", renderCommands);
elements["mobile-menu"].addEventListener("click", () => elements.sidebar.classList.toggle("open"));
window.addEventListener("beforeunload", disconnectEvents);

loadConversations().catch(showError);
