import { useEffect, useRef, useState } from 'react';
import { ApiError, request } from './api';
import { GraphCanvas } from './GraphCanvas';
import { Inspector } from './Inspector';
import { RunPanel } from './RunPanel';
import { edgeColors, exampleLayout, isTerminal, kinds, parseGraph } from './model';
import type { Compiled, Descriptor, EdgeKind, Graph, Publication, Run, Validation } from './types';

type Published = { metadata: Publication; graph: Graph };
type RunSnapshot = { graph: Graph; fixture: string };
const initialToken = import.meta.env.DEV ? 'local-spike-runner' : '';
const message = (error: unknown) => error instanceof Error ? error.message : 'The request failed. Check the backend connection.';
const nextVersion = (version: string) => `0.0.${Number(version.split('.')[2]) + 1}`;

export function App() {
  const [token, setToken] = useState(initialToken);
  const [connectedToken, setConnectedToken] = useState('');
  const [catalog, setCatalog] = useState<Descriptor[]>([]);
  const [draft, setDraft] = useState<Graph | null>(null);
  const [selected, setSelected] = useState<string | null>('policy');
  const [layers, setLayers] = useState<EdgeKind[]>(['control', 'capability']);
  const [view, setView] = useState<'draft' | 'run'>('draft');
  const [validation, setValidation] = useState<Validation | null>(null);
  const [compiled, setCompiled] = useState<Compiled | null>(null);
  const [published, setPublished] = useState<Published | null>(null);
  const [version, setVersion] = useState('0.0.1');
  const [fixture, setFixture] = useState('low-risk');
  const [run, setRun] = useState<Run | null>(null);
  const [runSnapshot, setRunSnapshot] = useState<RunSnapshot | null>(null);
  const [pollError, setPollError] = useState('');
  const [pollEpoch, setPollEpoch] = useState(0);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const mutationLock = useRef(false);
  const mounted = useRef(true);
  const importInput = useRef<HTMLInputElement>(null);
  const canvasSection = useRef<HTMLElement>(null);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function perform(label: string, operation: () => Promise<void>) {
    if (mutationLock.current) return;
    mutationLock.current = true; setBusy(label); setError(''); setNotice('');
    try { await operation(); }
    catch (e) {
      if (mounted.current) {
        setError(message(e));
        if (e instanceof ApiError && Array.isArray(e.body.errors)) setValidation(e.body as unknown as Validation);
      }
    } finally { mutationLock.current = false; if (mounted.current) setBusy(''); }
  }
  async function connect() {
    await perform('Connecting…', async () => {
      const [types, example] = await Promise.all([
        request<Descriptor[]>('/node-types', token), request<Graph>('/graph-examples/pr-review', token),
      ]);
      setCatalog(types); setConnectedToken(token);
      if (!draft) setDraft(exampleLayout(example));
      setNotice('Connected to the local Studio API. Reviews use Java fixtures.');
    });
  }
  useEffect(() => { if (initialToken) void connect(); }, []); // One initial dev connection; explicit reconnect thereafter.

  function change(graph: Graph) {
    if (mutationLock.current || view !== 'draft') return;
    setDraft(graph); setValidation(null); setCompiled(null); setNotice(''); setError('');
  }
  async function validate() {
    if (!draft) return;
    await perform('Validating…', async () => {
      const result = await request<Validation>('/graphs/validate', connectedToken, draft);
      setValidation(result); setNotice(result.valid ? 'Draft is valid. It can be compiled and published.' : 'The draft needs changes before publishing.');
    });
  }
  async function compile() {
    if (!draft) return;
    await perform('Compiling…', async () => {
      setCompiled(await request<Compiled>('/graphs/compile', connectedToken, { version, graph: draft }));
      setNotice('Compiled preview is available below the graph. This has not published a version.');
    });
  }
  async function refreshPublication(publication: Published) {
    const metadata = await request<Publication>(`/publications/${publication.metadata.publicationId}`, connectedToken);
    const current = { ...publication, metadata }; setPublished(current); return current;
  }
  async function publish() {
    if (!draft) return;
    await perform('Publishing…', async () => {
      const result = await request<Validation>('/graphs/validate', connectedToken, draft);
      setValidation(result);
      if (!result.valid) { setNotice('Fix the validation diagnostics before publishing.'); return; }
      const snapshot = structuredClone(draft);
      let metadata: Publication;
      try { metadata = await request<Publication>('/graphs/publish', connectedToken, { version, graph: snapshot }); }
      catch (e) {
        if (!(e instanceof ApiError) || e.status >= 500) throw new Error(`Publication outcome is uncertain. No automatic retry was made. Check the backend before publishing this version again. ${message(e)}`);
        throw e;
      }
      const publication = { metadata, graph: snapshot }; setPublished(publication);
      setVersion(nextVersion(version)); setNotice(`Version ${metadata.version} accepted. Waiting for runtime discovery.`);
    });
  }
  const pendingId = published?.metadata.status === 'PUBLISHING' ? published.metadata.publicationId : null;
  useEffect(() => {
    if (!pendingId || !published) return;
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
    const deadline = Date.now() + 30_000;
    async function poll() {
      try {
        const metadata = await request<Publication>(`/publications/${pendingId}`, connectedToken, undefined, controller.signal);
        if (controller.signal.aborted) return;
        setPublished(p => p?.metadata.publicationId === pendingId ? { ...p, metadata } : p);
        if (metadata.status === 'READY') setNotice(`Version ${metadata.version} is registered and ready to run.`);
        else if (metadata.status === 'FAILED_TO_DEPLOY') setError('The workflow artifact could not be deployed. Inspect the backend before choosing a new version.');
        else if (Date.now() < deadline) timer = setTimeout(poll, 500);
        else setError('Runtime discovery is taking longer than 30 seconds. Recheck the publication status before running.');
      } catch (e) { if (!controller.signal.aborted) setError(`Could not read publication status. Use Recheck status. ${message(e)}`); }
    }
    void poll(); return () => { controller.abort(); clearTimeout(timer); };
  }, [pendingId, connectedToken]);

  async function startRun() {
    if (!published || published.metadata.status !== 'READY') return;
    await perform('Starting fixture…', async () => {
      const snapshot = { graph: structuredClone(published.graph), fixture };
      const initial = { publicationId: published.metadata.publicationId, version: published.metadata.version,
        events: [], output: null, lostEvents: 0 };
      let started: { runId: string; status: string };
      try { started = await request(`/publications/${published.metadata.publicationId}/runs`, connectedToken, { fixture }); }
      catch (e) {
        if (e instanceof ApiError && e.body.code === 'RUN_START_UNCERTAIN' && typeof e.body.runId === 'string') {
          setRun({ ...initial, runId: e.body.runId, status: 'START_UNCERTAIN' }); setRunSnapshot(snapshot); setView('run');
          setPollError('Run start is uncertain. The backend saved the run intent.'); return;
        }
        if (!(e instanceof ApiError) || e.status >= 500) {
          // The POST may have reached the server even if its response was lost.
          setRun({ ...initial, runId: 'Response unavailable', status: 'START_UNCERTAIN' }); setRunSnapshot(snapshot);
          throw new Error(`Run start outcome is uncertain. Check the backend before starting another run. ${message(e)}`);
        }
        throw e;
      }
      setRun({ ...initial, ...started }); setRunSnapshot(snapshot); setView('run'); setSelected(null); setPollError(''); setPollEpoch(e => e + 1);
      setNotice(`Started ${fixture} on published version ${published.metadata.version}.`);
    });
  }
  const runId = run?.runId;
  useEffect(() => {
    if (!runId || runId === 'Response unavailable') return;
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
    const deadline = Date.now() + 30_000;
    async function poll() {
      try {
        const result = await request<Run>(`/graph-runs/${runId}`, connectedToken, undefined, controller.signal);
        if (controller.signal.aborted) return;
        setRun(result); setPollError('');
        if (!isTerminal(result) && Date.now() < deadline) timer = setTimeout(poll, 500);
        else if (!isTerminal(result)) setPollError('Still waiting for a terminal event after 30 seconds. Refresh events to continue observing this run.');
      } catch (e) { if (!controller.signal.aborted) setPollError(`Event polling stopped. ${message(e)}`); }
    }
    void poll(); return () => { controller.abort(); clearTimeout(timer); };
  }, [runId, connectedToken, pollEpoch]);

  function download() {
    const link = document.createElement('a'); const url = URL.createObjectURL(new Blob([JSON.stringify(draft, null, 2)], { type: 'application/json' }));
    link.href = url; link.download = 'agent-studio.graph.json'; link.click(); URL.revokeObjectURL(url);
  }
  async function importGraph(file?: File) {
    if (!file) return;
    try {
      if (file.size > 60_000) throw new Error('Graph files must be smaller than 60 KB.');
      const graph = parseGraph(await file.text());
      setDraft(graph); setValidation(null); setCompiled(null); setSelected(null); setView('draft'); setError('');
      setNotice('Graph imported. Validate it against the backend catalog before publishing.');
    } catch (e) { setError(message(e)); }
    if (importInput.current) importInput.current.value = '';
  }
  const graph = view === 'run' && runSnapshot ? runSnapshot.graph : draft;
  const dirty = !!published && JSON.stringify(draft) !== JSON.stringify(published.graph);
  const activeRun = !!run && !['COMPLETED', 'FAILED'].includes(run.status);
  const ready = published?.metadata.status === 'READY';
  const validVersion = /^0\.0\.[1-9][0-9]{0,8}$/.test(version);

  return <main data-main-thread-ui>
    <header className="app-header"><div className="brand"><a href="https://www.the-main-thread.com" target="_blank" rel="noreferrer"><img src="/main-thread-wordmark.png" alt="The Main Thread" width="168" height="32" /></a><span className="header-divider" /><h1>Agent Studio</h1></div>
      <span className="fixture-label">Fixture reviews · Local companion</span></header>
    <div className="intro"><p>Shape a workflow. Publish a version. Follow its execution.</p>
      <details className="connection-settings" open={!draft}><summary>{connectedToken ? 'API connection' : 'Connect to the backend'}</summary>
        <div className="connection-form"><label>Studio token<input type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} /></label>
          <button disabled={!!busy || !token.trim()} onClick={connect}>Connect</button></div>
        <p className="help">Held in memory for this tab. Local dev token: <code>local-spike-runner</code>.</p>
      </details></div>
    <div className="announcements" aria-live="polite" aria-atomic="true">{busy || notice || (!draft ? 'Start the Quarkus backend, then connect.' : 'Draft changes stay in this tab. Download the graph to keep them.')}</div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {!draft ? <div className="loading-workspace"><h2>{busy ? 'Loading the graph catalog…' : 'Your workflow starts here.'}</h2><p>The editor loads its node types and example from the local Studio API.</p></div>
      : <>
        <section className="draft-toolbar" aria-label="Draft actions"><label className="graph-name">Graph name<input value={draft.name} maxLength={120} disabled={!!busy || view !== 'draft'} onChange={e => change({ ...draft, name: e.target.value })} /></label>
          <div className="draft-file-actions"><button disabled={!!busy} onClick={download}>Download graph</button><button disabled={!!busy} onClick={() => importInput.current?.click()}>Import graph</button>
            <input ref={importInput} type="file" accept=".json,application/json" hidden onChange={e => void importGraph(e.target.files?.[0])} /></div>
          <div className="publish-actions"><label>Next version<input className="version-input" value={version} pattern="0\.0\.[1-9][0-9]{0,8}" disabled={!!busy} onChange={e => setVersion(e.target.value)} /></label>
            <button disabled={!!busy || view !== 'draft'} onClick={validate}>Validate</button>
            <button className="primary" disabled={!!busy || !validVersion || view !== 'draft'} onClick={publish}>Publish version</button></div>
        </section>
        {!validVersion && <p className="notice warning">Use a version from 0.0.1 to 0.0.999999999.</p>}
        <section className="publication-bar" aria-label="Published version"><div><strong>{published ? `Published ${published.metadata.version}` : 'No published version'}</strong>
          <span>{published ? published.metadata.status.replaceAll('_', ' ') : 'Validate and publish to enable fixture runs.'}</span>
          {dirty && <span className="warning-text">Draft has unpublished changes.</span>}
          {published && published.metadata.status !== 'READY' && <button disabled={!!busy} onClick={() => perform('Checking runtime…', async () => { await refreshPublication(published); })}>Recheck status</button>}
        </div><div className="run-actions"><label>PR fixture<select value={fixture} onChange={e => setFixture(e.target.value)} disabled={!!busy || activeRun}>
          <option value="low-risk">Low risk · Documentation</option><option value="high-risk">High risk · Security change</option></select></label>
          <button className="primary" disabled={!!busy || !ready || activeRun} onClick={startRun}>{published ? `Run v${published.metadata.version}` : 'Run fixture'}</button></div>
        </section>
        {validation && <section className={`validation-summary ${validation.valid ? 'success' : 'error'}`} aria-label="Validation results">
          <strong>{validation.valid ? 'Valid graph' : `${validation.errors.length} validation ${validation.errors.length === 1 ? 'diagnostic' : 'diagnostics'}`}</strong>
          {!validation.valid && <ul>{validation.errors.map((d, i) => <li key={i}>{d.nodeId && <button className="text-button" onClick={() => { setView('draft'); setSelected(d.nodeId); }}>{d.nodeId}</button>} {d.message} <code>{d.path || d.code}</code></li>)}</ul>}
        </section>}
        <section className="workspace" ref={canvasSection} aria-label="Workflow workbench">
          <div className="canvas-column"><div className="canvas-toolbar"><div className="view-switch" role="group" aria-label="Graph view">
            <button aria-pressed={view === 'draft'} onClick={() => { setView('draft'); setSelected('policy'); }}>Draft graph</button>
            <button aria-pressed={view === 'run'} disabled={!runSnapshot} onClick={() => { setView('run'); setSelected(null); }}>Execution graph</button></div>
            <fieldset className="edge-layers"><legend>Connections</legend>{kinds.map(kind => <label key={kind}><input type="checkbox" checked={layers.includes(kind)}
              onChange={e => setLayers(e.target.checked ? [...layers, kind] : layers.filter(k => k !== kind))} /><span style={{ color: edgeColors[kind] }}>{kind[0].toUpperCase() + kind.slice(1)}</span></label>)}</fieldset>
          </div><div className="canvas-caption">{view === 'draft' ? `${draft.nodes.length} nodes · Pan or zoom to inspect. Use the node list for settings.` : `Read-only run snapshot · v${run?.version} · Node states come from recorded events.`}</div>
            {graph && <GraphCanvas key={view + (view === 'run' ? run?.runId : draft.flowId)} graph={graph} catalog={catalog} selected={selected} select={setSelected}
              change={change} layers={layers} readOnly={!!busy || view !== 'draft'} run={view === 'run' ? run ?? undefined : undefined}
              invalidNodes={view === 'draft' ? validation?.errors.flatMap(d => d.nodeId ? [d.nodeId] : []) ?? [] : []} />}
          </div>
          {graph && <Inspector graph={graph} catalog={catalog} selected={selected} select={setSelected} change={change} readOnly={!!busy || view !== 'draft'} />}
        </section>
        <details className="compiled-panel"><summary>Compiled workflow and publication details</summary><p className="help">Preview the draft's generated workflow. The server validates the graph before compiling.</p>
          <button disabled={!!busy || !validVersion || view !== 'draft'} onClick={compile}>Compile draft preview</button>
          {published && <dl><dt>Publication ID</dt><dd><code>{published.metadata.publicationId}</code></dd><dt>Published artifact SHA-256</dt><dd><code>{published.metadata.artifactSha256}</code></dd></dl>}
          {compiled && <><h3>Draft preview · {compiled.version}</h3><pre>{compiled.yaml}</pre><details><summary>Resolved task manifest</summary><pre>{JSON.stringify(compiled.manifest, null, 2)}</pre></details></>}
        </details>
        <RunPanel run={run} graph={runSnapshot?.graph} fixture={runSnapshot?.fixture} pollError={pollError} refresh={() => setPollEpoch(e => e + 1)}
          inspect={id => { setView('run'); setSelected(id ?? null); canvasSection.current?.scrollIntoView({ block: 'start', behavior: 'instant' }); }} />
      </>}
    <footer><span>Deterministic review. Real workflow execution.</span><span>Drafts live in this tab; download to keep your work.</span></footer>
  </main>;
}
