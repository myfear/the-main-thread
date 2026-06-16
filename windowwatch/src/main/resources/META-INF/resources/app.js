const budgetPercent = document.getElementById("budgetPercent");
const budgetTotals = document.getElementById("budgetTotals");
const budgetFill = document.getElementById("budgetFill");
const budgetLabel = document.getElementById("budgetLabel");
const budgetLegend = document.getElementById("budgetLegend");
const requestInput = document.getElementById("requestInput");
const requestOutput = document.getElementById("requestOutput");
const requestMax = document.getElementById("requestMax");
const requestNote = document.getElementById("requestNote");
const ledger = document.getElementById("ledger");
const ledgerTotal = document.getElementById("ledgerTotal");
const answer = document.getElementById("answer");
const promptInput = document.getElementById("prompt");
const sendButton = document.getElementById("send");
const autoSendButton = document.getElementById("autoSend");
const memoryIdLabel = document.getElementById("memoryIdLabel");

const STORAGE_KEY = "windowwatch.memoryId";

const stressPrompts = [
  "Remember customer Orbital Freight, incident ORB-17, and a 14 minute outage.",
  "Add that the outage was isolated to eu-central and involved delayed invoice sync.",
  "Also remember that support promised a same-day postmortem and a credit review.",
  "Summarize what happened so far in two sentences.",
  "Now add that the root cause looks like a stale webhook signature after key rotation.",
  "List the customer facts, technical facts, and promised follow-ups separately.",
  "Rewrite the whole situation as a short handoff note for the next engineer."
];

let autoSendIndex = 0;

const memoryId = sessionStorage.getItem(STORAGE_KEY) ?? crypto.randomUUID();
sessionStorage.setItem(STORAGE_KEY, memoryId);
memoryIdLabel.textContent = memoryId;

function formatTokens(value) {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  if (value >= 1000) {
    return `~${(value / 1000).toFixed(1)}k`;
  }
  return String(value);
}

function formatPercent(ratio) {
  if (ratio == null || Number.isNaN(ratio)) {
    return "0%";
  }
  return `${Math.round(Math.max(0, Math.min(1, ratio)) * 100)}%`;
}

function addLegendRow(list, label, tokens, color) {
  const item = document.createElement("li");
  item.innerHTML = `
    <span class="legend-label">
      <span class="legend-swatch" style="background:${color}"></span>
      ${label}
    </span>
    <span class="legend-value">${formatTokens(tokens)}</span>`;
  list.appendChild(item);
}

function fillColor(state) {
  if (state === "danger") {
    return "hsl(0 72% 52%)";
  }
  if (state === "warning") {
    return "hsl(36 82% 47%)";
  }
  return "hsl(176 58% 36%)";
}

function renderBudgetGauge(budget) {
  const used = budget.usedTokens ?? 0;
  const max = budget.maxTokens ?? 1;
  const ratio = Math.max(0, Math.min(1, budget.fillRatio ?? 0));
  const available = budget.availableTokens ?? Math.max(0, max - used);
  const retainedTurns = budget.retainedTurnTokens ?? 0;
  const otherRetained = budget.otherRetainedTokens ?? Math.max(0, used - retainedTurns);
  const evicted = budget.evictedMessageTokens ?? 0;

  budgetPercent.textContent = `${formatPercent(ratio)} full`;
  budgetTotals.textContent = `${formatTokens(used)} / ${formatTokens(max)}`;
  budgetLabel.textContent = `${formatTokens(used)} / ${formatTokens(max)}`;
  budgetFill.style.height = `${ratio * 100}%`;
  budgetFill.style.backgroundColor = fillColor(budget.state);

  budgetLegend.replaceChildren();
  addLegendRow(budgetLegend, "Retained turn messages", retainedTurns, "var(--seg-messages)");
  if (otherRetained > 0) {
    addLegendRow(budgetLegend, "Other retained memory", otherRetained, "#4a5568");
  }
  addLegendRow(budgetLegend, "Headroom in budget", available, "var(--seg-available)");
  if (evicted > 0) {
    addLegendRow(budgetLegend, "Evicted from memory", evicted, "var(--seg-evicted)");
  }
}

function renderRequestDiagnostics(budget) {
  requestInput.textContent = formatTokens(budget.lastRequestInputTokens);
  requestOutput.textContent = formatTokens(budget.lastRequestOutputTokens);
  requestMax.textContent = formatTokens(budget.configuredModelMaxTokens);

  if (budget.lastRequestInputTokens != null) {
    requestNote.textContent =
      "Ollama counted these tokens on the last call. The tank on the left is the retained-memory budget LangChain4j is enforcing.";
  } else {
    requestNote.textContent =
      "Send a prompt to populate per-call model usage. The tank on the left is still the main budget.";
  }
}

function renderAnswer(text) {
  answer.textContent = text ?? "";
}

function renderBudget(budget) {
  renderBudgetGauge(budget);
  renderRequestDiagnostics(budget);

  ledger.replaceChildren();

  let cumulative = 0;
  for (const turn of budget.turns) {
    cumulative += turn.userTokens + turn.assistantTokens;

    const userRow = document.createElement("div");
    userRow.className = "ledger-row" + (turn.userActiveInWindow ? "" : " inactive");
    userRow.innerHTML = `<span>U${turn.turn}</span><span>${turn.userTokens}</span>`;
    ledger.appendChild(userRow);

    const assistantRow = document.createElement("div");
    assistantRow.className = "ledger-row" + (turn.assistantActiveInWindow ? "" : " inactive");
    assistantRow.innerHTML = `<span>A${turn.turn}</span><span>${turn.assistantTokens}</span>`;
    ledger.appendChild(assistantRow);
  }

  ledgerTotal.textContent =
    `Σ ${formatTokens(cumulative)} historical · ${formatTokens(budget.retainedTurnTokens)} retained in turn rows`;
}

async function sendPrompt(prompt) {
  if (!prompt?.trim()) {
    return;
  }

  sendButton.disabled = true;
  autoSendButton.disabled = true;

  try {
    const response = await fetch(`/api/chat/${memoryId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt })
    });

    if (!response.ok) {
      throw new Error(`Request failed: ${response.status}`);
    }

    const payload = await response.json();
    renderAnswer(payload.answer);
    renderBudget(payload.budget);
  } finally {
    sendButton.disabled = false;
    autoSendButton.disabled = false;
  }
}

async function loadBudget() {
  const response = await fetch(`/api/budget/${memoryId}`);
  if (response.ok) {
    renderBudget(await response.json());
  }
}

sendButton.addEventListener("click", () => {
  const prompt = promptInput.value;
  sendPrompt(prompt).then(() => {
    promptInput.value = "";
  });
});

autoSendButton.addEventListener("click", () => {
  const prompt = stressPrompts[autoSendIndex % stressPrompts.length];
  autoSendIndex += 1;
  promptInput.value = prompt;
  sendPrompt(prompt);
});

promptInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    sendButton.click();
  }
});

loadBudget();
