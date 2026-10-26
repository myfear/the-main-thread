import type { Graph, Run } from './types';
import { canRefreshRun } from './model';

export function RunPanel({ run, graph, fixture, pollError, refresh, inspect }: {
  run: Run | null; graph?: Graph; fixture?: string; pollError: string; refresh: () => void; inspect: (nodeId?: string) => void;
}) {
  return <section className="run-panel" aria-labelledby="run-heading">
    <div className="section-heading"><h2 id="run-heading">Execution</h2>{run && <span role="status" className={`status ${run.status === 'FAILED' ? 'error' : run.status === 'COMPLETED' ? 'success' : ''}`}>{run.status.replaceAll('_', ' ')}</span>}</div>
    {!run ? <div className="empty-result"><h3>Follow a real fixture run.</h3><p>Publish the draft, then run a bundled pull request. The decision, task states, and lifecycle events will appear here.</p></div>
      : <>
        <div className="run-meta"><span>Version <code>{run.version}</code></span><span>{fixture} fixture</span><span>{run.events.length} recorded events</span><button onClick={() => inspect()}>Inspect execution graph</button></div>
        <p className="run-id">Run <code>{run.runId}</code></p>
        {pollError && <div className="notice error" role="alert">{pollError}</div>}
        {canRefreshRun(run, pollError) && <button onClick={refresh}>Refresh events</button>}
        {run.status === 'START_UNCERTAIN' && <p className="notice warning">The server could not confirm the start. Do not start it again automatically. Keep this run ID for investigation.</p>}
        {run.lostEvents > 0 && <p className="notice warning">The journal reports {run.lostEvents} lost events in this backend process. This execution view may be incomplete.</p>}
        {run.output ? <div className={`decision ${run.output.requiresApproval ? 'needs-review' : 'low-risk'}`}>
          <div><h3>{run.output.requiresApproval ? 'Human review required' : 'Low-risk result'}</h3><p>{run.output.reviewResult.summary}</p>
            <p className="help">Fixture result · {run.output.requiresApproval ? 'This is a routing decision; no approval task is created.' : 'No pull request is approved or changed.'}</p></div>
          <ul className="findings">{run.output.reviewResult.findings.map((f, i) => <li key={i}><strong>{f.severity} · {f.category}</strong><p>{f.message}</p><code>{f.file}:{f.line}</code></li>)}</ul>
        </div> : <p className="notice">{run.status === 'FAILED' ? 'The workflow failed. Inspect the recorded task failure below.' : 'Waiting for a terminal workflow event. Task states below come only from recorded events.'}</p>}
        <details className="event-details" open><summary>Lifecycle events <span className="muted">Emitted by Quarkus Flow</span></summary>
          <p className="help">Saved by Studio in PostgreSQL and fetched every 500 ms while the run is active. Capability nodes have no task events.</p>
          <div className="event-table-wrap"><table><thead><tr><th scope="col">Recorded time</th><th scope="col">Event</th><th scope="col">Graph node</th></tr></thead>
            <tbody>{run.events.map(e => <tr key={e.eventId}><td><time dateTime={e.occurredAt}>{new Date(e.occurredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, fractionalSecondDigits: 3 })}</time></td>
              <td><code>{e.kind}</code>{e.error != null && <pre className="event-error">{JSON.stringify(e.error, null, 2)}</pre>}</td>
              <td>{e.nodeId ? <button className="text-button" disabled={!graph?.nodes.some(n => n.id === e.nodeId)} onClick={() => inspect(e.nodeId!)}>{e.nodeId}</button> : <span className="muted">Workflow</span>}</td></tr>)}</tbody></table></div>
          {!run.events.length && <p className="help">No events recorded yet.</p>}
        </details>
        <details><summary>Response JSON</summary><pre>{JSON.stringify(run, null, 2)}</pre></details>
      </>}
  </section>;
}
