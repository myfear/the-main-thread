import './app.css';
import { TypingPause, waitingReason } from './input-policy.js';

const $ = id => document.getElementById(id);
const types = ['EVENT', 'REMINDER', 'CHECKLIST', 'TIMER', 'SPLIT', 'NOTE'];
const useCache = new URLSearchParams(location.search).get('cache') !== '0';
const examples = [
  ['Dinner tomorrow', 'Dinner with Priya tomorrow at 8pm'],
  ['A little reminder', 'Remind me to pay rent tomorrow at 9am'],
  ['The shopping list', 'Buy milk, eggs, bread and coffee'],
  ['Time to focus', '25 min focus'],
  ['Share the bill', 'Split 120.50 between 3'],
  ['A passing thought', 'The best ideas arrive during a walk']
];
let config = {debounceMs: 250, maximumDebounceMs: 1000, adaptiveDebounce: true, minimumProbability: .55, minimumMargin: .15, inputRules: []};
let configReady = false, composing = false;
const typingPause = new TypingPause();
let revision = 0, lastInputAt = 0, debounceTimer, inFlight = false, forced = null;
let draft = null, displayedText = '', timerHandle, timerDeadline = 0, remaining = 0, editingId = null;
let saved;
try { saved = JSON.parse(localStorage.getItem('shapeshift-local-v1') || '[]'); if (!Array.isArray(saved)) saved = []; }
catch { saved = []; }
const metrics = window.shapeshiftMetrics = {edits: 0, requests: 0, suppressedInputs: 0, staleResponses: 0, errors: 0, maxInFlight: 0, events: []};

function element(tag, text, cls) { const e = document.createElement(tag); if (text !== undefined) e.textContent = text; if (cls) e.className = cls; return e; }
function button(text, action) { const b = element('button', text); b.type = 'button'; b.addEventListener('click', action); return b; }
function record(event) { metrics.events.push(event); if (metrics.events.length > 2000) metrics.events.shift(); }
async function post(path, body) {
  const response = await fetch(path, {method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body)});
  if (!response.ok) throw new Error(response.status === 429 ? 'Model busy. Pause typing or choose a card.' : 'Local model unavailable. Start Ollama or choose a card.');
  return response.json();
}
function setStatus(text) { $('status').textContent = text; }
function refreshPalette(active) { for (const b of $('palette').children) b.setAttribute('aria-pressed', String(b.dataset.type === active)); }
for (const type of types) { const b = button(type[0] + type.slice(1).toLowerCase(), () => choose(type)); b.dataset.type = type; b.setAttribute('aria-pressed','false'); $('palette').append(b); }
for (const [label, text] of examples) $('examples').append(button(label, () => { forced = null; editingId = null; $('thought').value = text; changed(); $('thought').focus(); }));

function changed(event) {
  const text = $('thought').value;
  revision++; lastInputAt = performance.now(); metrics.edits++;
  typingPause.edit(text, lastInputAt);
  $('save').disabled = true; clearTimeout(debounceTimer);
  if (!text.trim()) { draft = null; forced = null; editingId = null; typingPause.reset(); renderCard(); setStatus('Ready when you are.'); return; }
  if (!forced) { draft = null; renderCard(); }
  if (composing || event?.isComposing) { setStatus('Finish composing your thought.'); return; }
  if (!forced && (!configReady || showWaiting(text))) return;
  setStatus(forced ? 'Updating your chosen card…' : 'Listening…');
  schedule();
}
function showWaiting(text) {
  const reason = waitingReason(text, config.inputRules);
  if (!reason) return false;
  metrics.suppressedInputs++;
  draft = null; renderCard(); setStatus(reason);
  $('timing').textContent = 'KEEP TYPING'; $('source').textContent = 'Add a detail, or choose a card yourself.';
  record({kind: 'waiting', text, accepted: false, reason});
  return true;
}
function schedule() {
  clearTimeout(debounceTimer);
  if (composing || (!forced && (!configReady || waitingReason($('thought').value, config.inputRules)))) return;
  const delay = typingPause.delay(config);
  debounceTimer = setTimeout(() => forced ? choose(forced) : classify(), Math.max(0, delay - (performance.now() - lastInputAt)));
}
async function classify() {
  if (inFlight || composing || !configReady || !$('thought').value.trim() || showWaiting($('thought').value)) return;
  const id = revision, text = $('thought').value, inputAt = lastInputAt, started = performance.now();
  const debounceMs = typingPause.delay(config);
  inFlight = true; metrics.requests++; metrics.maxInFlight = Math.max(metrics.maxInFlight, 1);
  setStatus('Finding a shape…');
  try {
    const result = await post('/api/intent', {text, useCache});
    const requestMs = performance.now() - started;
    if (id !== revision || forced) { metrics.staleResponses++; record({text, stale: true, requestMs}); return; }
    const p = result.prediction, probability = p.probabilities[p.intent] || 0;
    const accepted = result.ready && p.intent !== 'NONE' && probability >= config.minimumProbability && p.margin >= config.minimumMargin;
    draft = accepted ? result.card : null; displayedText = text;
    renderCard();
    setStatus(accepted ? 'A shape for your thought. Edit it or choose another.' : result.waitingReason || 'Keep typing, or choose a card.');
    $('timing').textContent = `${Math.round(requestMs)} MS · ${p.cached ? 'CACHED' : p.model.toUpperCase()}`;
    $('source').textContent = `Local model · ${Math.round(probability * 100)}% for ${p.intent.toLowerCase()}${p.urgent >= .65 ? ' · urgent' : ''}`;
    $('diagnostics').textContent = JSON.stringify({...result, clientRequestMs: Math.round(requestMs), inputToCardMs: Math.round(performance.now() - inputAt)}, null, 2);
    requestAnimationFrame(() => { if (id === revision) record({kind: 'decision', text, stale: false, intent: p.intent, accepted, cached: p.cached, source: result.source, debounceMs, inferenceMs: p.inferenceMillis, serverMs: result.serverMillis, requestMs, inputToCardMs: performance.now() - inputAt}); });
  } catch (error) {
    metrics.errors++; record({text, error: String(error), requestMs: performance.now() - started});
    if (id === revision) { setStatus(error.message); $('source').textContent = 'No model decision. Choose a card to continue.'; }
  } finally {
    inFlight = false;
    if (id !== revision && !forced && $('thought').value.trim()) schedule();
  }
}
async function choose(type) {
  clearTimeout(debounceTimer);
  forced = type; const id = revision, text = $('thought').value; $('save').disabled = true;
  try {
    const card = await post('/api/parse', {intent: type, text});
    if (id !== revision || forced !== type) return;
    draft = card; displayedText = text; renderCard(); setStatus('Your choice. Edit the details, then save.');
    $('source').textContent = 'Card chosen by you · deterministic parsing';
  } catch { if (id === revision) setStatus('Could not parse the card. Check the application connection.'); }
}
function field(container, label, key, type = 'text', options = {}) {
  const wrapper = element('label', label), input = element('input'); input.type = type; input.value = draft.fields[key] ?? '';
  for (const [k, v] of Object.entries(options)) input[k] = v;
  input.addEventListener('input', () => { draft.fields[key] = type === 'number' ? input.valueAsNumber : input.value; });
  wrapper.append(input); container.append(wrapper); return input;
}
function renderCard() {
  clearInterval(timerHandle); timerHandle = null; timerDeadline = 0;
  $('card').replaceChildren(); refreshPalette(draft?.type);
  delete $('card').dataset.type;
  if (!draft) { const box = element('div', undefined, 'empty'); box.append(element('span','✳','empty-symbol'),element('p','A few words are enough to begin.')); $('card').append(box); return; }
  $('card').dataset.type = draft.type; $('card').append(element('span', draft.type.toLowerCase(), 'card-label'));
  const fields = element('div', undefined, 'fields');
  if (draft.type === 'EVENT' || draft.type === 'REMINDER') {
    field(fields, 'Title', 'title'); field(fields, 'Date', 'date', 'date'); field(fields, 'Time', 'time', 'time');
    $('card').append(fields, element('p', `Time zone: ${draft.fields.zone || config.zone}`, 'warning'));
  } else if (draft.type === 'CHECKLIST') {
    draft.fields.items ||= [];
    draft.fields.checked ||= [];
    for (let i = 0; i < draft.fields.items.length; i++) {
      const row = element('div',undefined,'check-row'), check = element('input'), input = element('input');
      check.type = 'checkbox'; check.checked = Boolean(draft.fields.checked[i]); check.setAttribute('aria-label', `Complete item ${i + 1}`);
      check.addEventListener('change', () => { draft.fields.checked[i] = check.checked; });
      input.type = 'text'; input.value = draft.fields.items[i]; input.setAttribute('aria-label', `Item ${i + 1}`); input.addEventListener('input', () => { draft.fields.items[i] = input.value; });
      row.append(check, input); $('card').append(row);
    }
    $('card').append(button('Add item', () => { draft.fields.items.push(''); renderCard(); }));
  } else if (draft.type === 'TIMER') {
    field(fields, 'Title', 'title'); const duration = field(fields, 'Duration (seconds)', 'seconds', 'number', {min:1,max:86400});
    remaining = Number(draft.fields.seconds || 0); const readout = element('p','', 'timer-readout'); readout.id = 'timer-readout';
    const show = () => { readout.textContent = `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2,'0')}`; };
    show(); duration.addEventListener('input', () => { clearInterval(timerHandle); timerHandle = null; timerDeadline = 0; remaining = Number.isFinite(duration.valueAsNumber) ? Math.max(0, Math.floor(duration.valueAsNumber)) : 0; show(); });
    const start = button('Start / pause', () => {
      if (timerHandle) { clearInterval(timerHandle); timerHandle = null; return; }
      if (remaining < 1 || remaining > 86400) return;
      timerDeadline = Date.now() + remaining * 1000;
      timerHandle = setInterval(() => { remaining = Math.max(0, Math.ceil((timerDeadline - Date.now()) / 1000)); show(); if (!remaining) { clearInterval(timerHandle); timerHandle = null; setStatus('Timer finished.'); } }, 200);
    });
    $('card').append(fields, readout, start);
  } else if (draft.type === 'SPLIT') {
    const total = field(fields,'Total amount','total','number',{min:0,max:1000000,step:'0.01'}), people = field(fields,'People','people','number',{min:1,max:1000,step:1});
    const readout = element('p','', 'split-readout'), detail = element('p','','warning');
    const calculate = () => {
      const match = total.value.match(/^(\d+)(?:\.(\d{1,2}))?$/), count = Number(people.value);
      if (!match || !Number.isInteger(count) || count < 1 || count > 1000 || Number(total.value) > 1000000) { readout.textContent = 'Confirm amount and people'; detail.textContent = ''; return; }
      const cents = Number(match[1]) * 100 + Number((match[2] || '').padEnd(2, '0'));
      const base = Math.floor(cents / count), remainder = cents % count;
      readout.textContent = `${(base / 100).toFixed(2)} each`;
      detail.textContent = remainder ? `${remainder} ${remainder === 1 ? 'person pays' : 'people pay'} one extra cent to balance the total.` : 'Split equally, down to the cent.';
    };
    total.addEventListener('input',calculate); people.addEventListener('input',calculate); calculate(); $('card').append(fields, readout, detail);
  } else {
    const label = element('label','Your note'), note = element('textarea',undefined,'note-text'); note.value = draft.fields.title || ''; note.addEventListener('input', () => { draft.fields.title = note.value; }); label.append(note); $('card').append(label);
  }
  for (const warning of draft.warnings || []) $('card').append(element('p',warning,'warning'));
  $('save').disabled = displayedText !== $('thought').value;
}
function save() {
  if (!draft || $('save').disabled) return;
  const item = {id: editingId || crypto.randomUUID(), text: displayedText, card: structuredClone(draft)};
  const next = saved.filter(old => old.id !== item.id); next.unshift(item);
  try { localStorage.setItem('shapeshift-local-v1', JSON.stringify(next)); } catch { setStatus('Browser storage is unavailable. Your card has not been saved.'); return; }
  saved = next; editingId = null; $('thought').value = ''; changed(); renderSaved();
}
function renderSaved() {
  $('saved-count').textContent = `${saved.length} saved`; $('saved').replaceChildren();
  for (const item of saved) {
    const e = element('article',undefined,'saved-item'); e.append(element('small', item.card.type), element('p',item.card.fields.title || item.card.fields.items?.join(', ') || item.text));
    e.append(button('Edit', () => { revision++; clearTimeout(debounceTimer); editingId = item.id; forced = item.card.type; $('thought').value = item.text; displayedText = item.text; draft = structuredClone(item.card); renderCard(); setStatus('Editing a saved card.'); }));
    e.append(button('Delete', () => { const next = saved.filter(old => old.id !== item.id); try { localStorage.setItem('shapeshift-local-v1',JSON.stringify(next)); saved = next; renderSaved(); } catch { setStatus('Browser storage is unavailable.'); } }));
    $('saved').append(e);
  }
}
$('thought').addEventListener('input', changed);
$('thought').addEventListener('compositionstart', () => { composing = true; revision++; clearTimeout(debounceTimer); $('save').disabled = true; if (!forced) { draft = null; renderCard(); } });
$('thought').addEventListener('compositionend', () => { composing = false; changed(); });
$('thought').addEventListener('keydown', event => { if (composing || event.isComposing) return; if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); save(); } if (event.key === 'Escape') { $('thought').value = ''; changed(); } });
$('save').addEventListener('click',save);
$('debounce').addEventListener('input', () => { const value = $('debounce').valueAsNumber; if (Number.isFinite(value)) config.debounceMs = Math.max(0, Math.min(2000, value)); });
fetch('/api/config').then(r=>r.json()).then(value=>{ config = value; configReady = true; $('model-info').textContent = `${value.model} · ${value.questionCount} decision questions · ${value.zone}`; if ($('thought').value.trim() && !forced && !showWaiting($('thought').value)) schedule(); }).catch(()=>setStatus('Could not connect to the application.'));
renderSaved();
