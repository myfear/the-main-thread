import { describe, ticketNumber, request, showError } from "./shared.js";

const $ = (id) => document.getElementById(id);
const stages = ["QUEUED", "PREPARING", "READY"];
const statusCopy = {
  QUEUED: [
    "You’re in the queue.",
    "Your order is with the stall. A warm cup is on its way.",
  ],
  PREPARING: [
    "A little spice. A little patience.",
    "We’re pouring your drink. Stay close to the counter.",
  ],
  READY: [
    "Your Glühwein is ready!",
    "Come over to Stall No. 25 and show your ticket.",
  ],
};
let current = null;
let source = null;
let paused = false;
let probeInFlight = false;
let missing = false;
let logStarted = false;

function log(message) {
  if (!logStarted) {
    $("event-log").replaceChildren();
    logStarted = true;
  }
  const item = document.createElement("li");
  item.textContent = `${new Date().toLocaleTimeString()} — ${message}`;
  $("event-log").prepend(item);
  while ($("event-log").children.length > 12)
    $("event-log").lastElementChild.remove();
}
function connection(label, live, stale = "") {
  $("connection-state").textContent = label;
  $("connection-state").classList.toggle("live", live);
  $("stale-note").textContent = stale;
  $("stale-note").hidden = !stale;
}
function render(order) {
  if (current && order.id === current.id && order.revision < current.revision)
    return;
  current = order;
  $("name").value = order.name;
  $("quantity").value = String(order.quantity);
  document.querySelectorAll('input[name="drink"]').forEach((input) => {
    input.checked = input.value === order.drink;
  });
  $("order-form")
    .querySelectorAll("input, select")
    .forEach((input) => {
      input.disabled = true;
    });
  $("empty-ticket").hidden = true;
  $("active-ticket").hidden = false;
  $("ticket-number").textContent = ticketNumber(order);
  $("ticket-name").textContent = `For ${order.name}`;
  $("ticket-drink").textContent = describe(order);
  const [title, description] = statusCopy[order.status];
  $("status-title").textContent =
    order.status === "READY" && order.drink === "APPLE"
      ? "Your apple punch is ready!"
      : title;
  $("status-description").textContent = description;
  $("order-status").classList.toggle("ready", order.status === "READY");
  document.querySelectorAll("[data-stage]").forEach((item) => {
    item.classList.toggle(
      "complete",
      stages.indexOf(item.dataset.stage) <= stages.indexOf(order.status),
    );
    if (item.dataset.stage === order.status)
      item.setAttribute("aria-current", "step");
    else item.removeAttribute("aria-current");
  });
  $("payload").textContent = JSON.stringify(order, null, 2);
  $("place-order").disabled = true;
  $("place-order").textContent = "Order placed";
  document.title = `${ticketNumber(order)} · ${order.status === "READY" ? "Ready to collect" : "Glühwein live orders"}`;
}
function stopStream() {
  source?.close();
  source = null;
}
function connect(id) {
  stopStream();
  paused = false;
  missing = false;
  $("toggle-stream").disabled = false;
  $("toggle-stream").textContent = "Disconnect";
  connection(
    "Connecting…",
    false,
    "Waiting for the latest state from the stall.",
  );
  const stream = new EventSource(`/api/orders/${id}/events`);
  source = stream;
  stream.addEventListener("order", (event) => {
    if (source !== stream) return;
    const order = JSON.parse(event.data);
    render(order);
    connection("Live updates connected", true);
    showError($("ticket-error"));
    log(`Received revision ${order.revision}: ${order.status.toLowerCase()}.`);
  });
  stream.onerror = async () => {
    if (source !== stream) return;
    connection(
      "Reconnecting…",
      false,
      "Connection lost. This ticket may be out of date. We’ll reconnect automatically.",
    );
    // EventSource hides the HTTP status. Check only to distinguish an expired ticket.
    if (probeInFlight) return;
    probeInFlight = true;
    try {
      await request(`/api/orders/${id}`);
    } catch (error) {
      if (source === stream && error.status === 404) {
        stopStream();
        missing = true;
        $("toggle-stream").disabled = true;
        connection(
          "Ticket unavailable",
          false,
          "The demo has restarted. Place a new order to begin again.",
        );
        showError($("ticket-error"), error.message);
      }
    } finally {
      probeInFlight = false;
    }
  };
}
$("order-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (current) return;
  showError($("form-error"));
  const name = $("name").value.trim();
  if (!name) {
    showError($("form-error"), "Enter a first name for your ticket.");
    $("name").focus();
    return;
  }
  $("place-order").disabled = true;
  $("place-order").textContent = "Placing your order…";
  try {
    const data = new FormData(event.currentTarget);
    const order = await request("/api/orders", {
      method: "POST",
      body: JSON.stringify({
        name,
        drink: data.get("drink"),
        quantity: Number(data.get("quantity")),
      }),
    });
    history.replaceState(null, "", `/?order=${order.id}`);
    render(order);
    log(`Order accepted as ${ticketNumber(order)}.`);
    connect(order.id);
    $("ticket-title").focus();
  } catch (error) {
    showError(
      $("form-error"),
      error.status
        ? error.message
        : "We couldn’t confirm the order. Check the stall counter before placing it again.",
    );
    $("place-order").disabled = false;
    $("place-order").textContent = "Place your order →";
  }
});
$("toggle-stream").addEventListener("click", () => {
  if (!current || missing) return;
  if (paused) {
    log("Reconnecting: requesting a fresh snapshot.");
    connect(current.id);
  } else {
    stopStream();
    paused = true;
    $("toggle-stream").textContent = "Reconnect";
    connection(
      "Disconnected for demo",
      false,
      "Updates are paused. Prepare your drink at the counter, then reconnect to catch up.",
    );
    log("Disconnected deliberately. The stall keeps working.");
  }
});
$("new-order").addEventListener("click", () => {
  stopStream();
  current = null;
  paused = false;
  history.replaceState(null, "", "/");
  $("active-ticket").hidden = true;
  $("empty-ticket").hidden = false;
  $("ticket-number").textContent = "No order yet";
  $("place-order").disabled = false;
  $("place-order").textContent = "Place your order →";
  $("order-form")
    .querySelectorAll("input, select")
    .forEach((input) => {
      input.disabled = false;
    });
  showError($("ticket-error"));
  $("payload").textContent = "Place an order to inspect its snapshot.";
  document.title = "Glühwein & good company · The Main Thread";
  log("Started a new ticket. Previous orders remain at the counter.");
  $("name").focus();
});
const savedId = new URLSearchParams(location.search).get("order");
if (savedId) {
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      savedId,
    )
  ) {
    showError(
      $("ticket-error"),
      "This ticket link is invalid. Place a new order.",
    );
  } else {
    $("place-order").disabled = true;
    request(`/api/orders/${savedId}`)
      .then((order) => {
        render(order);
        connect(order.id);
      })
      .catch((error) => {
        showError(
          $("ticket-error"),
          error.status
            ? error.message
            : "Could not load your saved ticket. Reload when the stall is available.",
        );
        $("place-order").disabled = false;
      });
  }
}
window.addEventListener("pagehide", stopStream);
window.addEventListener("pageshow", (event) => {
  if (event.persisted && current && !paused && !missing) connect(current.id);
});
