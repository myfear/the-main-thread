import './styles.css';
import bundledSnapshot from '../../fixtures/quarkus-runtime.snapshot.json';
import { layoutCity } from './layout';
import { CityScene } from './scene';
import { searchTypes, sourceLocation, type ColorMetric, type Snapshot, type SnapshotType } from './model';
import { parseSnapshot, SnapshotError, validateSnapshot } from './validation';

function byId<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error('Missing interface element: ' + id);
  return element as T;
}
const ui = {
  open: byId<HTMLButtonElement>('open-file'),
  file: byId<HTMLInputElement>('file-input'),
  sample: byId<HTMLButtonElement>('load-sample'),
  search: byId<HTMLInputElement>('search'),
  color: byId<HTMLSelectElement>('color-metric'),
  reset: byId<HTMLButtonElement>('reset-camera'),
  focus: byId<HTMLButtonElement>('focus-selected'),
  results: byId<HTMLUListElement>('type-list'),
  host: byId<HTMLDivElement>('canvas-host'),
  cityMessage: byId<HTMLDivElement>('city-message'),
  tooltip: byId<HTMLDivElement>('tooltip'),
  copy: byId<HTMLButtonElement>('copy-location'),
};
const number = new Intl.NumberFormat('en');
let snapshot: Snapshot | null = null;
let typesById = new Map<string, SnapshotType>();
let selectedId: string | null = null;
let scene: CityScene | null = null;
let canRender = false;
let busy = false;
let importSequence = 0;

function updateControls(): void {
  ui.open.disabled = busy;
  ui.open.textContent = busy ? 'Opening…' : 'Open JSON';
  ui.sample.disabled = busy;
  ui.search.disabled = busy || !snapshot;
  ui.color.disabled = busy || !snapshot?.types.length;
  ui.reset.disabled = busy || !canRender || !snapshot?.types.length;
  ui.focus.disabled = busy || !canRender || !selectedId;
}

function showCityMessage(heading: string, text: string): void {
  byId('city-message-heading').textContent = heading;
  byId('city-message-text').textContent = text;
  ui.cityMessage.hidden = false;
}

function unavailable(): void {
  canRender = false;
  ui.host.dataset.state = 'unavailable';
  ui.tooltip.hidden = true;
  byId('legend').hidden = true;
  showCityMessage('3D view unavailable',
    'WebGL 2 could not start or its context was lost. You can still search types and inspect metrics. Enable WebGL 2 in your browser, then reload to restore the city.');
  updateControls();
}

function initializeScene(): void {
  try {
    scene = new CityScene(ui.host, {
      select: selectType,
      hover: showTooltip,
      unavailable,
      rendered: () => {
        if (!snapshot || !canRender) return;
        ui.host.dataset.state = snapshot.types.length ? 'ready' : 'empty';
        if (snapshot.types.length) ui.cityMessage.hidden = true;
      },
    });
    canRender = true;
  } catch {
    unavailable();
  }
}

function displayDiagnostics(data: Snapshot): void {
  const details = byId<HTMLDetailsElement>('diagnostics');
  details.hidden = data.diagnostics.length === 0;
  details.open = data.summary.incomplete;
  const errors = data.diagnostics.filter(diagnostic => diagnostic.severity === 'error').length;
  byId('diagnostics-summary').textContent = data.summary.incomplete
    ? 'Incomplete snapshot · ' + errors + ' analysis error' + (errors === 1 ? '' : 's')
    : data.diagnostics.length + ' analysis note' + (data.diagnostics.length === 1 ? '' : 's');
  byId('diagnostics-description').textContent = data.summary.incomplete
    ? 'Some source files or types were omitted. Counts describe verified, included types.'
    : 'These notes describe source the analyzer intentionally skipped.';
  const fragment = documentFragment();
  for (const diagnostic of data.diagnostics) {
    const item = document.createElement('li');
    item.textContent = diagnostic.severity + ' · ' + diagnostic.code + ' · ' + diagnostic.path
      + (diagnostic.line === null ? '' : ':' + diagnostic.line) + ' — ' + diagnostic.message
      + (diagnostic.affectedTypeIds.length ? ' Omitted IDs: ' + diagnostic.affectedTypeIds.join(', ') : '');
    fragment.append(item);
  }
  byId('diagnostics-list').replaceChildren(fragment);
}

function documentFragment(): DocumentFragment {
  return document.createDocumentFragment();
}

function writeIdentifier(element: HTMLElement, value: string, separator: '.' | '/' | null): void {
  const parts = separator ? value.split(separator).map((part, index, all) =>
    part + (index < all.length - 1 ? separator : '')) : value.split(/(?=[A-Z][a-z])/).filter(Boolean);
  element.replaceChildren();
  parts.forEach((part, index) => {
    if (index) element.append(document.createElement('wbr'));
    element.append(document.createTextNode(part));
  });
}

function revealSelectedResult(): void {
  const button = ui.results.querySelector<HTMLButtonElement>('button[aria-pressed="true"]');
  if (!button) return;
  const item = button.getBoundingClientRect();
  const container = ui.results.getBoundingClientRect();
  if (item.top < container.top) ui.results.scrollTop += item.top - container.top - 4;
  else if (item.bottom > container.bottom) ui.results.scrollTop += item.bottom - container.bottom + 4;
}

function openSnapshot(document: Snapshot, source: string): void {
  snapshot = document;
  typesById = new Map(document.types.map(type => [type.id, type]));
  selectedId = null;
  ui.search.value = '';
  ui.tooltip.hidden = true;
  byId('import-error').hidden = true;
  byId('repository-name').textContent = document.repository.displayName;
  const modules = new Set(document.types.map(type => type.module)).size;
  const packages = new Set(document.types.map(type => type.module + '::' + type.packageName)).size;
  byId('snapshot-summary').textContent = 'Snapshot · ' + number.format(document.types.length) + ' types · '
    + modules + ' module' + (modules === 1 ? '' : 's') + ' · ' + packages + ' package' + (packages === 1 ? '' : 's');
  const commit = byId('commit');
  commit.textContent = document.revisions.head.commit.slice(0, 10);
  commit.title = document.revisions.head.commit;
  byId('status').textContent = source + (document.summary.incomplete ? ' · Incomplete analysis.' : '');
  byId('selection-empty').hidden = false;
  byId('selection-details').hidden = true;
  byId('copy-status').textContent = '';
  displayDiagnostics(document);
  const layout = layoutCity(document.types);
  if (canRender && scene) {
    ui.host.dataset.state = 'loading';
    scene.load(document.types, layout);
    if (document.types.length === 0) {
      showCityMessage('No types in this snapshot',
        'Choose another snapshot or widen the analyzer include patterns to include named Java types.');
    }
  }
  byId('legend').hidden = !canRender || document.types.length === 0;
  renderResults();
  updateControls();
}

function renderResults(): void {
  if (!snapshot) return;
  const matches = searchTypes(snapshot.types, ui.search.value);
  const fragment = documentFragment();
  const tabStop = matches.find(type => type.id === selectedId)?.id ?? matches[0]?.id;
  for (const type of matches) {
    const item = document.createElement('li');
    const button = document.createElement('button');
    button.type = 'button';
    button.dataset.typeId = type.id;
    button.setAttribute('aria-pressed', String(type.id === selectedId));
    button.tabIndex = type.id === tabStop ? 0 : -1;
    const name = document.createElement('span');
    name.className = 'result-name';
    writeIdentifier(name, type.typeName, null);
    const context = document.createElement('span');
    context.className = 'result-context';
    writeIdentifier(context, type.module + ' · ' + (type.packageName || '(default package)'), '.');
    button.append(name, context);
    item.append(button);
    fragment.append(item);
  }
  ui.results.replaceChildren(fragment);
  byId('result-count').textContent = number.format(matches.length);
  byId('no-results').hidden = matches.length > 0;
  byId('no-results').textContent = snapshot.types.length === 0
    ? 'This snapshot contains no named types.'
    : 'No types match. Try a shorter name or clear the search.';
  updateColors(new Set(matches.map(type => type.id)));
  revealSelectedResult();
}

function updateColors(matches?: ReadonlySet<string>): void {
  if (!snapshot) return;
  const metric = ui.color.value as ColorMetric;
  const matchedIds = matches ?? new Set(searchTypes(snapshot.types, ui.search.value).map(type => type.id));
  if (canRender) scene?.color(metric, ui.search.value.trim() ? matchedIds : null);
  const maximum = snapshot.types.reduce((max, type) => Math.max(max, type.metrics.head[metric]), 0);
  byId('legend-title').textContent = ui.color.selectedOptions[0]?.textContent ?? 'Methods';
  byId('legend-max').textContent = number.format(maximum);
}

function selectType(id: string): void {
  const type = typesById.get(id);
  if (!type) return;
  selectedId = id;
  ui.tooltip.hidden = true;
  scene?.select(id);
  byId('selection-empty').hidden = true;
  byId('selection-details').hidden = false;
  writeIdentifier(byId('selected-name'), type.qualifiedName, '.');
  byId('selected-kind').textContent = type.typeKind.head;
  byId('method-count').textContent = number.format(type.metrics.head.methodCount);
  byId('constructor-count').textContent = number.format(type.metrics.head.constructorCount);
  byId('field-count').textContent = number.format(type.metrics.head.fieldCount);
  byId('source-lines').textContent = number.format(type.metrics.head.sourceLines);
  writeIdentifier(byId('selected-module'), type.module, '/');
  writeIdentifier(byId('selected-package'), type.packageName || '(default package)', '.');
  writeIdentifier(byId('selected-source'), sourceLocation(type), '/');
  byId('selected-span').textContent = 'Lines ' + type.source.head.startLine + '–' + type.source.head.endLine
    + ' · snapshot (not compared)';
  byId('selected-id').textContent = type.id;
  byId('copy-status').textContent = '';
  for (const button of ui.results.querySelectorAll<HTMLButtonElement>('button')) {
    const selected = button.dataset.typeId === id;
    button.setAttribute('aria-pressed', String(selected));
    button.tabIndex = selected ? 0 : -1;
  }
  // Keep a visible tab stop even when the selection is outside the search result.
  if (!ui.results.querySelector('button[tabindex="0"]')) {
    const first = ui.results.querySelector<HTMLButtonElement>('button');
    if (first) first.tabIndex = 0;
  }
  revealSelectedResult();
  updateControls();
}

function showTooltip(id: string | null, x: number, y: number): void {
  const type = id ? typesById.get(id) : null;
  ui.tooltip.hidden = !type;
  if (!type) return;
  byId('tooltip-name').textContent = type.typeName;
  byId('tooltip-metrics').textContent = number.format(type.metrics.head.methodCount) + ' declared methods';
  byId('tooltip-module').textContent = type.module + ' · ' + (type.packageName || '(default package)');
  ui.tooltip.style.left = Math.max(12, Math.min(x + 14, window.innerWidth - ui.tooltip.offsetWidth - 12)) + 'px';
  ui.tooltip.style.top = Math.max(12, Math.min(y + 14, window.innerHeight - ui.tooltip.offsetHeight - 12)) + 'px';
}

async function importFile(file: File): Promise<void> {
  const sequence = ++importSequence;
  busy = true;
  byId('status').textContent = 'Opening ' + file.name + '…';
  updateControls();
  try {
    let text: string;
    try {
      text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
    } catch {
      throw new SnapshotError('The file could not be read as UTF-8 JSON. Choose another snapshot.');
    }
    const loaded = parseSnapshot(text);
    if (sequence !== importSequence) return;
    openSnapshot(loaded, 'Local file: ' + file.name);
  } catch (error) {
    if (sequence !== importSequence) return;
    byId('error-message').textContent = file.name + ': '
      + (error instanceof SnapshotError ? error.message : 'The file could not be opened. Choose another snapshot.');
    byId('import-error').hidden = false;
    byId('status').textContent = snapshot ? 'Current snapshot retained.' : 'Choose a valid snapshot to continue.';
  } finally {
    if (sequence === importSequence) {
      busy = false;
      updateControls();
    }
  }
}

ui.open.addEventListener('click', () => ui.file.click());
ui.file.addEventListener('change', () => {
  const file = ui.file.files?.[0];
  ui.file.value = '';
  if (file) void importFile(file);
});
ui.sample.addEventListener('click', () => {
  importSequence++;
  busy = false;
  openSnapshot(validateSnapshot(bundledSnapshot), 'Bundled Quarkus sample');
});
byId('dismiss-error').addEventListener('click', () => { byId('import-error').hidden = true; });
ui.search.addEventListener('input', renderResults);
ui.search.addEventListener('keydown', event => {
  if (event.key === 'ArrowDown') {
    const first = ui.results.querySelector<HTMLButtonElement>('button');
    if (first) { event.preventDefault(); first.focus(); }
  }
});
ui.color.addEventListener('change', () => updateColors());
ui.reset.addEventListener('click', () => scene?.reset());
ui.focus.addEventListener('click', () => { if (selectedId) scene?.focus(selectedId); });
ui.results.addEventListener('click', event => {
  const button = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('button[data-type-id]') : null;
  if (button?.dataset.typeId) selectType(button.dataset.typeId);
});
ui.results.addEventListener('keydown', event => {
  const buttons = [...ui.results.querySelectorAll<HTMLButtonElement>('button')];
  const index = buttons.findIndex(button => button === document.activeElement);
  if (index < 0) return;
  const next = event.key === 'ArrowDown' ? Math.min(index + 1, buttons.length - 1)
    : event.key === 'ArrowUp' ? Math.max(0, index - 1)
      : event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : -1;
  if (next >= 0) {
    event.preventDefault();
    buttons.forEach((button, position) => { button.tabIndex = position === next ? 0 : -1; });
    buttons[next]?.focus();
  }
});
ui.copy.addEventListener('click', async () => {
  const id = selectedId;
  const type = id ? typesById.get(id) : null;
  if (!type) return;
  try {
    await navigator.clipboard.writeText(sourceLocation(type));
    if (selectedId === id) byId('copy-status').textContent = 'Source location copied.';
  } catch {
    if (selectedId === id) byId('copy-status').textContent = 'Could not copy. Select and copy the source path above.';
  }
});

let dragDepth = 0;
document.addEventListener('dragenter', event => {
  if (event.dataTransfer?.types.includes('Files')) {
    event.preventDefault();
    dragDepth++;
    byId('drop-hint').hidden = false;
  }
});
document.addEventListener('dragover', event => {
  if (event.dataTransfer?.types.includes('Files')) {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
  }
});
document.addEventListener('dragleave', () => {
  dragDepth = Math.max(0, dragDepth - 1);
  if (!dragDepth) byId('drop-hint').hidden = true;
});
document.addEventListener('drop', event => {
  event.preventDefault();
  dragDepth = 0;
  byId('drop-hint').hidden = true;
  const file = event.dataTransfer?.files[0];
  if (file) void importFile(file);
});
// Wrapping and panel height change at responsive breakpoints. Keep selection in
// the list's own viewport without moving the document or the user's camera.
const resultsResizeObserver = new ResizeObserver(revealSelectedResult);
resultsResizeObserver.observe(ui.results);
window.addEventListener('beforeunload', () => {
  resultsResizeObserver.disconnect();
  scene?.dispose();
});

initializeScene();
try {
  openSnapshot(validateSnapshot(bundledSnapshot), 'Bundled Quarkus sample');
} catch {
  showCityMessage('The sample could not be opened', 'Choose a valid Software City snapshot JSON.');
  byId('status').textContent = 'Choose a local snapshot to continue.';
  updateControls();
}
