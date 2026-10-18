import { describe, ticketNumber, request, showError } from "./shared.js";
const $ = (id) => document.getElementById(id);
const cards = new Map();
let connected = false;
let source;
let orders = [];
let pendingId = null;
function textElement(tag, text, className) {
  const element = document.createElement(tag);
  element.textContent = text;
  if (className) element.className = className;
  return element;
}
function render() {
  const activeId = document.activeElement?.closest("[data-id]")?.dataset.id;
  const counts = { QUEUED: 0, PREPARING: 0, READY: 0 };
  const ids = new Set(orders.map((order) => order.id));
  for (const [id, card] of cards)
    if (!ids.has(id)) {
      card.remove();
      cards.delete(id);
    }
  document
    .querySelectorAll(".lane-empty")
    .forEach((element) => element.remove());
  for (const order of orders) {
    counts[order.status]++;
    let card = cards.get(order.id);
    if (!card) {
      card = document.createElement("article");
      card.className = "counter-order";
      card.dataset.id = order.id;
      cards.set(order.id, card);
    }
    const heading = textElement("h3", ticketNumber(order));
    heading.append(textElement("span", `Revision ${order.revision}`));
    card.replaceChildren(
      heading,
      textElement("p", order.name, "customer"),
      textElement("p", describe(order), "drink"),
    );
    if (order.status !== "READY") {
      const button = textElement(
        "button",
        pendingId === order.id
          ? "Updating…"
          : order.status === "QUEUED"
            ? "Start preparing →"
            : "Mark ready →",
        "primary",
      );
      button.type = "button";
      button.disabled = !connected || pendingId === order.id;
      button.setAttribute(
        "aria-label",
        `${order.status === "QUEUED" ? "Start preparing" : "Mark ready"} order ${ticketNumber(order)}`,
      );
      button.addEventListener("click", () => advance(order));
      card.append(button);
    } else {
      card.append(textElement("p", "Ready at Stall No. 25", "collected-note"));
    }
    card.tabIndex = -1;
    const lane = $(`${order.status.toLowerCase()}-orders`);
    if (card.parentElement !== lane) lane.append(card);
  }
  for (const [state, count] of Object.entries(counts)) {
    $(`${state.toLowerCase()}-count`).textContent = count;
    if (!count)
      $(`${state.toLowerCase()}-orders`).append(
        textElement(
          "p",
          state === "READY"
            ? "The next warm cup belongs here."
            : "No orders here yet.",
          "lane-empty",
        ),
      );
  }
  $("empty-board").hidden = orders.length !== 0;
  if (activeId && cards.has(activeId))
    (
      cards.get(activeId).querySelector("button:not(:disabled)") ||
      cards.get(activeId)
    ).focus({
      preventScroll: true,
    });
}
async function advance(order) {
  if (pendingId === order.id || !connected) return;
  pendingId = order.id;
  showError($("counter-error"));
  render();
  try {
    await request(`/api/orders/${order.id}/advance`, {
      method: "POST",
      body: JSON.stringify({ revision: order.revision }),
    });
  } catch (error) {
    showError(
      $("counter-error"),
      error.status
        ? error.message
        : "Could not confirm the change. Check the live state before trying again.",
    );
  } finally {
    pendingId = null;
    render();
  }
}
function connect() {
  source?.close();
  source = new EventSource("/api/orders/events");
  source.addEventListener("board", (event) => {
    const firstConnection = !connected;
    connected = true;
    orders = JSON.parse(event.data);
    $("board-connection").textContent = "Counter connected";
    $("board-connection").classList.add("live");
    $("board-announcement").textContent = firstConnection
      ? `Counter connected. ${orders.length} orders loaded.`
      : "Order board updated.";
    render();
  });
  source.onerror = () => {
    connected = false;
    $("board-connection").textContent = "Reconnecting · orders may be stale";
    $("board-connection").classList.remove("live");
    render();
  };
}
connect();
window.addEventListener("pagehide", () => source?.close());
window.addEventListener("pageshow", (event) => {
  if (event.persisted) connect();
});
