const orderId = document.body.dataset.orderId;
const form = document.querySelector('#return-form');
const draftElement = document.querySelector('#draft');
const errorElement = document.querySelector('#error');
const statusElement = document.querySelector('#tool-status');
let order;
let draft = null;
let revision = 0;

const money = cents => new Intl.NumberFormat('en-IE', {
  style: 'currency', currency: order.currency
}).format(cents / 100);

function renderDraft() {
  draftElement.replaceChildren();
  if (!draft) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = 'No draft yet. Your order is unchanged.';
    draftElement.append(empty);
    return;
  }
  for (const item of draft.items) {
    const row = document.createElement('div');
    row.className = 'draft-line';
    const label = document.createElement('span');
    label.textContent = `${item.quantity} × ${item.name}`;
    const amount = document.createElement('span');
    amount.textContent = money(item.refundCents);
    row.append(label, amount);
    draftElement.append(row);
  }
  const total = document.createElement('p');
  total.className = 'total';
  const label = document.createElement('span');
  label.textContent = 'Estimated refund';
  const amount = document.createElement('span');
  amount.id = 'refund-total';
  amount.textContent = money(draft.refundCents);
  total.append(label, amount);
  const state = document.createElement('p');
  state.textContent = `Draft · ${draft.reason.replaceAll('_', ' ')} · Not submitted`;
  draftElement.append(total, state);
}

function syncForm() {
  for (const input of form.querySelectorAll('[data-sku]')) {
    input.value = draft?.items.find(item => item.sku === input.dataset.sku)?.quantity ?? 0;
  }
  document.querySelector('#reason').value = draft?.reason ?? 'damaged';
}

async function prepareReturn(input, { signal } = {}) {
  const thisRevision = ++revision;
  errorElement.textContent = '';
  try {
    const response = await fetch(`/api/orders/${encodeURIComponent(orderId)}/return-preview`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input), signal
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error ?? `Preview failed: HTTP ${response.status}`);
    // A later prepare/clear action owns the page now. Never overwrite it.
    if (thisRevision !== revision) throw new Error('Draft changed while this request was running. Read the current order again.');
    signal?.throwIfAborted();
    draft = result;
    syncForm();
    renderDraft();
    return JSON.stringify({ orderId, draft });
  } catch (error) {
    if (thisRevision === revision) errorElement.textContent = error.message;
    throw error;
  }
}

function clearDraft() {
  ++revision;
  draft = null;
  errorElement.textContent = '';
  syncForm();
  renderDraft();
  return JSON.stringify({ orderId, draft: null });
}

form.addEventListener('submit', event => {
  event.preventDefault();
  const items = [...form.querySelectorAll('[data-sku]')]
    .filter(input => !input.disabled && Number(input.value) > 0)
    .map(input => ({ sku: input.dataset.sku, quantity: Number(input.value) }));
  prepareReturn({ items, reason: document.querySelector('#reason').value }).catch(() => {});
});
document.querySelector('#clear').addEventListener('click', clearDraft);

async function registerTools() {
  if (!document.modelContext) {
    statusElement.textContent = 'WebMCP unavailable. The form is ready to use.';
    return;
  }
  const tools = [
    {
      name: 'get_current_order',
      description: 'Read the order open on this page, available SKUs, purchased quantities, prices in cents, returnability, and the current return draft.',
      inputSchema: { type: 'object', properties: {} },
      annotations: { readOnlyHint: true },
      execute: () => JSON.stringify({ order, draft })
    },
    {
      name: 'prepare_return',
      description: 'Replace the return draft on the current order page with a validated estimate for the selected items. Updates the visible quantities, reason, and refund total. The result is a draft for review; no return is submitted.',
      inputSchema: {
        type: 'object',
        properties: {
          items: {
            type: 'array', description: 'Distinct SKUs from the current order and quantities to return.',
            items: { type: 'object', properties: {
              sku: { type: 'string', description: 'SKU returned by get_current_order.' },
              quantity: { type: 'integer', description: 'Quantity to return, from 1 up to the purchased quantity.' }
            }, required: ['sku', 'quantity'] }
          },
          reason: { type: 'string', enum: ['damaged', 'wrong_item', 'changed_mind'] }
        }, required: ['items', 'reason']
      },
      execute: prepareReturn
    },
    {
      name: 'clear_return_draft',
      description: 'Clear the current return draft and reset the visible selection. The original order stays unchanged.',
      inputSchema: { type: 'object', properties: {} },
      execute: clearDraft
    }
  ];
  for (const tool of tools) await document.modelContext.registerTool(tool);
  statusElement.textContent = '3 WebMCP tools ready';
}

try {
  const response = await fetch(`/api/orders/${encodeURIComponent(orderId)}`);
  if (!response.ok) throw new Error(`Order could not be loaded: HTTP ${response.status}`);
  order = await response.json();
  document.querySelector('#fields').disabled = false;
  await registerTools();
} catch (error) {
  statusElement.textContent = 'Initialization failed. Reload the page to retry.';
  errorElement.textContent = error.message;
}
