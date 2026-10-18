export const drinks = {
  RED: "Classic Glühwein",
  WHITE: "White Glühwein",
  APPLE: "Spiced apple punch",
};
export function describe(order) {
  return `${order.quantity} ${order.quantity === 1 ? "cup" : "cups"} · ${drinks[order.drink]}`;
}
export function ticketNumber(order) {
  return `#${String(order.number).padStart(3, "0")}`;
}
export async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json" },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(
      body.message ||
        (response.status === 400
          ? "Check your name, drink, and number of cups, then try again."
          : `The request failed (${response.status}). Try again.`),
    );
    error.status = response.status;
    throw error;
  }
  return body;
}
export function showError(element, message = "") {
  element.textContent = message;
  element.hidden = !message;
}
