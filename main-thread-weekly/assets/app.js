(() => {
  const payload = JSON.parse(document.getElementById('issue-data').textContent);
  const { issue, concept } = payload;
  const storageKey = 'main-thread-weekly.read.v1';
  const form = document.querySelector('[data-filter-form]');
  const cards = [...document.querySelectorAll('[data-list] [data-item]')];
  const inspector = document.getElementById('reading-inspector');
  const desktopInspectorParent = inspector?.parentElement;
  const mobile = window.matchMedia('(max-width: 900px)');
  let selectedId = issue.items[0]?.id;
  let inspectorOpen = !mobile.matches;
  let marks = new Set();
  let persistent = true;

  function decodeMarks(value) {
    const result = JSON.parse(value || '[]');
    return new Set(Array.isArray(result) ? result.filter(v => typeof v === 'string') : []);
  }
  try { marks = decodeMarks(localStorage.getItem(storageKey)); }
  catch { persistent = false; }

  function announce(message) {
    document.querySelector('[data-notice]').textContent = message;
  }
  function save() {
    try { localStorage.setItem(storageKey, JSON.stringify([...marks])); }
    catch {
      persistent = false;
      announce('Reading marks work for this visit, but your browser cannot save them.');
    }
  }
  function syncMarks() {
    document.querySelectorAll('[data-read]').forEach(button => {
      const id = button.dataset.read;
      const item = issue.items.find(i => i.id === id);
      const read = marks.has(id);
      button.setAttribute('aria-pressed', String(read));
      button.setAttribute('aria-label', `Mark ${item.title} as ${read ? 'unread' : 'read'}`);
      button.querySelector('[data-read-label]').textContent = read ? 'Read' : 'Mark read';
    });
    cards.forEach(card => card.classList.toggle('is-read', marks.has(card.dataset.item)));
    const count = issue.items.filter(i => marks.has(i.id)).length;
    document.querySelectorAll('[data-progress]').forEach(node => { node.textContent = `${count} of ${issue.items.length} marked read`; });
    document.querySelectorAll('[data-progress-bar]').forEach(node => { node.value = count; });
    if (!persistent) document.querySelectorAll('.footer-notes p.enhancement').forEach(node => {
      node.textContent = `${count} of ${issue.items.length} marked read · this visit only`;
    });
  }
  function placeInspector() {
    if (!inspector) return;
    const row = cards.find(c => c.dataset.item === selectedId);
    if (mobile.matches && row && inspectorOpen) row.append(inspector);
    else desktopInspectorParent.append(inspector);
    inspector.hidden = !inspectorOpen;
  }
  function inspect(id, open = true) {
    if (!inspector || !payload.inspectorHtml[id]) return;
    selectedId = id;
    inspectorOpen = open;
    // HTML is rendered and escaped by Java from a validated, accepted issue.
    inspector.innerHTML = payload.inspectorHtml[id];
    cards.forEach(card => {
      const selected = card.dataset.item === id && inspectorOpen;
      card.classList.toggle('is-selected', selected);
      const control = card.querySelector('[data-inspect]');
      control?.setAttribute('aria-expanded', String(selected));
      if (control) control.firstChild.textContent = selected && mobile.matches ? 'Hide details' : 'Show details';
    });
    placeInspector();
    syncMarks();
  }
  function applyFilters() {
    const values = new FormData(form);
    const search = String(values.get('search') || '').trim().toLowerCase();
    const topic = String(values.get('topic') || 'all');
    const format = String(values.get('format') || 'all');
    const unread = values.has('unread');
    cards.forEach(card => {
      const matches = (!search || card.dataset.search.includes(search))
        && (topic === 'all' || card.dataset.topic.split('|').includes(topic))
        && (format === 'all' || card.dataset.kind === format)
        && (!unread || !marks.has(card.dataset.item));
      card.hidden = !matches;
    });
    const visible = cards.filter(card => !card.hidden);
    document.querySelector('[data-result-count]').textContent = `${visible.length} of ${cards.length} items${unread ? ' · unread' : ''}`;
    document.querySelector('[data-empty]').hidden = cards.length === 0 || visible.length !== 0;
    document.querySelectorAll('[data-topic-button]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.topicButton === topic));
    });
    if (inspector) {
      if (visible.length && !visible.some(card => card.dataset.item === selectedId)) inspect(visible[0].dataset.item, inspectorOpen);
      inspector.hidden = visible.length === 0 || !inspectorOpen;
    }
  }
  function clearFilters() {
    form.reset();
    if (form.elements.topic) form.elements.topic.value = 'all';
    applyFilters();
    form.elements.search.focus();
  }

  document.documentElement.classList.add('js');
  if (new URLSearchParams(location.search).has('embedded')) document.documentElement.classList.add('embedded');
  form.addEventListener('submit', event => event.preventDefault());
  form.addEventListener('input', applyFilters);
  form.addEventListener('change', applyFilters);
  document.addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.hasAttribute('data-read')) {
      const id = button.dataset.read;
      const read = !marks.has(id);
      if (read) marks.add(id); else marks.delete(id);
      save(); syncMarks(); applyFilters();
      const item = issue.items.find(i => i.id === id);
      announce(`${item.title} marked ${read ? 'read' : 'unread'}.${persistent ? '' : ' Saved for this visit only.'}`);
      if (button.closest('[hidden]')) {
        const next = cards.find(card => !card.hidden)?.querySelector('[data-read], [data-inspect]');
        (next || document.querySelector('[data-empty] [data-clear]')).focus();
      }
    } else if (button.hasAttribute('data-clear')) clearFilters();
    else if (button.hasAttribute('data-topic-button')) {
      form.elements.topic.value = button.dataset.topicButton;
      applyFilters();
    } else if (button.hasAttribute('data-inspect')) inspect(button.dataset.inspect, !(mobile.matches && selectedId === button.dataset.inspect && inspectorOpen));
    else if (button.hasAttribute('data-intent')) {
      const intent = button.dataset.intent;
      document.querySelectorAll('[data-intent]').forEach(control => control.setAttribute('aria-pressed', String(control === button)));
      document.getElementById('path-spotlight').innerHTML = payload.spotlightHtml[intent];
      document.querySelector('[data-intent-status]').textContent = `${intent} starting point: ${issue.items.find(i => i.id === issue.paths[intent]).title}`;
      syncMarks();
    } else if (button.hasAttribute('data-reset')) {
      marks.clear(); save(); syncMarks(); applyFilters();
      announce('Reading progress has been reset for all weeks.');
    }
  });
  window.addEventListener('storage', event => {
    if (event.key !== storageKey && event.key !== null) return;
    try { marks = decodeMarks(event.key === null ? null : event.newValue); syncMarks(); applyFilters(); } catch { /* Keep this visit usable. */ }
  });
  mobile.addEventListener('change', () => inspect(selectedId, !mobile.matches));
  if (concept === 'queue') inspect(selectedId, !mobile.matches);
  syncMarks(); applyFilters();
})();
