import { Component, useCallback, useRef, useState, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { Renderer, type OpenUIError } from '@openuidev/react-lang';
import { agUIAdapter } from '@openuidev/react-headless';
import { library } from './library';
import { examples } from './examples';
import { inspectOutput } from './output';
import { normalizeSse } from './stream';
import './style.css';

type Phase = 'idle' | 'connecting' | 'streaming' | 'complete' | 'cancelled' | 'error';
type Stats = { chunks: number; characters: number; elapsedMs: number };

class RenderBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <p role="alert">This report could not be rendered. Inspect its source or generate it again.</p> : this.props.children; }
}

function App() {
  const [notes, setNotes] = useState<string>(examples.workshop);
  const [source, setSource] = useState('');
  const [phase, setPhase] = useState<Phase>('idle');
  const [error, setError] = useState('');
  const [version, setVersion] = useState('');
  const [stats, setStats] = useState<Stats | null>(null);
  const [renderErrors, setRenderErrors] = useState<string[]>([]);
  const [runId, setRunId] = useState('');
  const controller = useRef<AbortController | null>(null);
  const busy = phase === 'connecting' || phase === 'streaming';

  const onRenderError = useCallback((errors: OpenUIError[]) => {
    const next = errors.filter(e => e.severity !== 'warning').map(e => e.message);
    setRenderErrors(previous => previous.join('\n') === next.join('\n') ? previous : next);
  }, []);

  async function generate() {
    if (controller.current) return;
    const abort = new AbortController();
    controller.current = abort;
    const id = crypto.randomUUID();
    setRunId(id); setSource(''); setStats(null); setError(''); setRenderErrors([]); setVersion(''); setPhase('connecting');
    let accumulated = '';
    let finished = false;
    let failure = false;
    try {
      const response = await fetch('/api/generate', { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, body: JSON.stringify({ runId: id, notes }), signal: abort.signal });
      if (!response.ok) throw new Error(response.status === 409 ? 'Another generation is running. Wait for it to finish, then try again.' : `The request failed (${response.status}). Check the notes and try again.`);
      for await (const event of agUIAdapter().parse(normalizeSse(response))) {
        if (abort.signal.aborted) break;
        if (event.type === 'TEXT_MESSAGE_CONTENT') { accumulated += event.delta; setSource(accumulated); setPhase('streaming'); }
        if (event.type === 'CUSTOM' && event.name === 'bob.session') { setVersion((event.value as { version: string }).version); }
        if (event.type === 'RUN_ERROR') { failure = true; throw new Error(event.message); }
        if (event.type === 'RUN_FINISHED') { finished = true; setStats(event.result as Stats); }
      }
      if (abort.signal.aborted) { setPhase('cancelled'); return; }
      if (!finished || failure) throw new Error('The connection ended before Bob finished. Generate the report again.');
      const parsed = inspectOutput(accumulated);
      if (!/^\s*root\s*=\s*Report\s*\(/.test(accumulated) || !parsed.root || parsed.meta.incomplete || parsed.meta.unresolved.length || parsed.meta.errors.length || parsed.queryStatements.length || parsed.mutationStatements.length) {
        throw new Error('Bob returned an incomplete or unsupported layout. Inspect the source and generate again.');
      }
      setPhase('complete');
    } catch (failure) {
      if (abort.signal.aborted) setPhase('cancelled');
      else { setPhase('error'); setError(failure instanceof Error ? failure.message : 'Generation failed. Try again.'); }
    } finally { controller.current = null; }
  }

  async function stop() {
    controller.current?.abort();
    setPhase('cancelled');
    try { await fetch(`/api/generate/${runId}`, { method: 'DELETE' }); }
    catch { setError('The stream stopped. The server timeout will close Bob if it cannot receive the stop request.'); }
  }

  function download() {
    const url = URL.createObjectURL(new Blob([source], { type: 'text/plain' }));
    const link = document.createElement('a'); link.href = url; link.download = 'bob-report.openui'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const labels: Record<Phase, string> = { idle: 'Ready for your notes', connecting: 'Connecting to Bob…', streaming: 'Bob is composing the report…', complete: 'Report generated', cancelled: 'Generation stopped', error: 'Report needs attention' };
  const status = phase === 'complete' && renderErrors.length ? 'Report needs attention' : labels[phase];
  return <main>
    <header className="page-header"><a className="publication" href="https://www.the-main-thread.com" target="_blank" rel="noopener noreferrer">The Main Thread</a><span className="pilot-label">Local pilot</span></header>
    <div className="intro"><h1>Bob report studio</h1><p>Describe an activity. Bob chooses how to present it.</p></div>
    <div className="workspace">
      <section className="input-pane" aria-labelledby="notes-title">
        <h2 id="notes-title">Activity notes</h2>
        <p className="help" id="notes-help">Include the facts you know. Missing information stays visible in the report.</p>
        <label htmlFor="notes">Your notes</label>
        <textarea id="notes" aria-describedby="notes-help" value={notes} maxLength={12000} onChange={event => setNotes(event.target.value)} disabled={busy} rows={11} />
        <fieldset disabled={busy}><legend>Try fictional example notes</legend><div className="examples"><button type="button" onClick={() => setNotes(examples.workshop)}>Workshop</button><button type="button" onClick={() => setNotes(examples.video)}>Video</button><button type="button" onClick={() => setNotes(examples.inconsistent)}>Conflicting counts</button></div></fieldset>
        <div className="actions"><button className="primary" onClick={generate} disabled={busy || !notes.trim()}>{busy ? 'Generating…' : 'Generate report'}</button>{busy && <button onClick={stop}>Stop generation</button>}</div>
        <p className="footnote">Notes are sent to IBM Bob. Generated reports are drafts for your review.</p>
      </section>
      <section className="output-pane" aria-label="Generated report" aria-busy={busy}>
        <div className="result-status"><span className={`status ${phase}`} role="status">{status}</span>{version && <span className="version">Bob {version}</span>}</div>
        {error && <div className="error" role="alert"><strong>Generation interrupted</strong><p>{error}</p></div>}
        {!busy && renderErrors.length > 0 && <div className="error" role="alert"><strong>The layout could not be fully rendered.</strong><p>Generate again or inspect the source below.</p></div>}
        {source ? <RenderBoundary key={runId}><Renderer library={library} response={source} isStreaming={busy} toolProvider={null} publishObservability={false} onError={onRenderError} /></RenderBoundary> : <div className="empty"><h2>{busy ? 'Preparing your report' : 'Your report takes shape here'}</h2><p>{busy ? 'Bob is opening a session and reading your notes. The layout will appear as its response arrives.' : 'Generate a report to see Bob arrange the facts, attendance and missing details into a readable view.'}</p>{busy && <div className="skeleton" aria-hidden="true"><span /><span /><span /></div>}</div>}
        {stats && <p className="stats">{(stats.elapsedMs / 1000).toFixed(1)} s · {stats.chunks} text chunks · {stats.characters.toLocaleString()} characters</p>}
        {source && <details className="source"><summary>Inspect generated OpenUI</summary><div className="source-tools"><p>Bob’s response, carried through ACP and AG-UI.</p><button onClick={download} disabled={busy}>Download source</button></div><pre tabIndex={0}>{source}</pre></details>}
      </section>
    </div>
    <footer>Bob Shell → ACP → Quarkus → AG-UI → OpenUI <span>Presentation only · no report is saved</span></footer>
  </main>;
}

createRoot(document.getElementById('root')!).render(<App />);
