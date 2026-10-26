import { useState } from 'react';
import { connectionEdge, defaultConfig, ports, removeNodes } from './model';
import type { Descriptor, Graph, GraphNode } from './types';

const labels: Record<string, string> = { minimumSeverity: 'Minimum severity', maxToolCalls: 'Maximum tool calls',
  outputSchemaRef: 'Output contract', modelRef: 'Model reference', promptRef: 'Prompt reference', allowedToolIds: 'Allowed tools' };
export function Inspector({ graph, catalog, selected, select, change, readOnly }: {
  graph: Graph; catalog: Descriptor[]; selected: string | null; select: (id: string | null) => void;
  change: (graph: Graph) => void; readOnly: boolean;
}) {
  const [addType, setAddType] = useState('task.review-policy');
  const node = graph.nodes.find(n => n.id === selected);
  const d = catalog.find(d => d.type === node?.type);
  function config(key: string, value: unknown) {
    if (node) change({ ...graph, nodes: graph.nodes.map(n => n.id === node.id ? { ...n, config: { ...n.config, [key]: value } } : n) });
  }
  function addNode() {
    const descriptor = catalog.find(d => d.type === addType)!;
    const node: GraphNode = { id: `node_${crypto.randomUUID().slice(0, 8)}`, type: descriptor.type, version: descriptor.version,
      position: { x: 345, y: 730 + (graph.nodes.length - 10) * 140 }, config: defaultConfig(descriptor) };
    change({ ...graph, nodes: [...graph.nodes, node] }); select(node.id);
  }
  return <aside className="inspector" aria-label="Graph inspector">
    <h2>{readOnly ? 'Inspect a node' : 'Node settings'}</h2>
    <label>Selected node<select aria-label="Selected node" value={selected ?? ''} onChange={e => select(e.target.value || null)}>
      <option value="">Choose a node</option>{graph.nodes.map(n => <option key={n.id} value={n.id}>{n.id} · {catalog.find(d => d.type === n.type)?.label ?? n.type}</option>)}
    </select></label>
    {node && d ? <>
      <div className="selection-heading"><strong>{d.label}</strong><code>{node.type} · v{node.version}</code></div>
      <fieldset disabled={readOnly} className="node-fields">
        {Object.entries(d.configSchema.properties).map(([key, property]) => <label key={key}>{labels[key] ?? key}
          {property.enum ? <select value={String(node.config[key])} onChange={e => config(key, e.target.value)}>
            {property.enum.map(value => <option key={value}>{value}</option>)}</select>
            : property.type === 'integer' ? <input type="number" min={property.minimum} max={property.maximum} step="1"
              value={typeof node.config[key] === 'number' ? node.config[key] : ''}
              onChange={e => config(key, e.target.value === '' ? '' : Number(e.target.value))} />
              : <input readOnly value={Array.isArray(node.config[key]) ? (node.config[key] as string[]).join(', ') : String(node.config[key] ?? '')} />}
        </label>)}
      </fieldset>
      {node.type === 'task.review-policy' && <p className="help">Findings at this severity or above require review. Files in a <code>security/</code> directory at any depth require review.</p>}
      {node.type === 'ai.agent' && <p className="help">Deterministic Java fixture. Attached tools are allowlisted and counted. No model provider is called.</p>}
      {!d.executable && <p className="help">Pins the single registered {node.type === 'ai.model-ref' ? 'fixture model' : node.type === 'ai.prompt-ref' ? 'prompt' : 'toolset'} in the publication. No alternative is selectable in this companion. This capability does not execute as a separate task.</p>}
      {!Object.keys(d.configSchema.properties).length && <p className="help">This node has no configurable fields. Its named ports define its inputs and route.</p>}
      <details className="ports-detail"><summary>Named ports</summary>
        {(['target', 'source'] as const).map(direction => <div key={direction}><strong>{direction === 'target' ? 'Inputs' : 'Outputs'}</strong>
          <ul>{ports(d, direction).map(p => <li key={`${p.kind}:${p.id}`}><code>{p.id}</code> <span className="muted">{p.kind}</span></li>)}</ul>
        </div>)}
      </details>
      {!readOnly && <button className="danger quiet" onClick={() => { change(removeNodes(graph, [node.id])); select(null); }}>Remove node and connections</button>}
    </> : <p className="help">Select a node on the canvas or from the list to inspect its contract and settings.</p>}
    {!readOnly && <details className="add-node"><summary>Add a node</summary><label>Node type
      <select value={addType} onChange={e => setAddType(e.target.value)}>{catalog.map(d => <option value={d.type} key={d.type}>{d.label}</option>)}</select>
    </label><button disabled={graph.nodes.length >= 32} onClick={addNode}>Add node</button></details>}
    <ConnectionEditor graph={graph} catalog={catalog} change={change} readOnly={readOnly} />
  </aside>;
}

function ConnectionEditor({ graph, catalog, change, readOnly }: { graph: Graph; catalog: Descriptor[]; change: (g: Graph) => void; readOnly: boolean }) {
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [message, setMessage] = useState('');
  function options(direction: 'source' | 'target') {
    return graph.nodes.flatMap(n => {
      const d = catalog.find(d => d.type === n.type);
      return d ? ports(d, direction).map(p => ({ value: `${n.id}|${p.kind}:${p.id}`, label: `${n.id} / ${p.kind} / ${p.id}` })) : [];
    });
  }
  function connect() {
    const [sourceNode, sourceHandle] = source.split('|');
    const [targetNode, targetHandle] = target.split('|');
    const edge = connectionEdge({ source: sourceNode, target: targetNode, sourceHandle, targetHandle }, graph, catalog);
    if (!edge) { setMessage('Choose compatible, available ports of the same kind. Use Validate to check the complete graph.'); return; }
    change({ ...graph, edges: [...graph.edges, edge] }); setMessage('Connection added. Validate the draft before publishing.');
  }
  return <details className="connections"><summary>Connections ({graph.edges.length})</summary>
    {!readOnly && <><p className="help">Connect handles on the canvas, or use these controls.</p>
      <label>Source port<select value={source} onChange={e => setSource(e.target.value)}><option value="">Choose source</option>
        {options('source').map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
      <label>Target port<select value={target} onChange={e => setTarget(e.target.value)}><option value="">Choose target</option>
        {options('target').map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
      <button disabled={!source || !target || graph.edges.length >= 64} onClick={connect}>Connect ports</button>
      {message && <p className="help" role="status">{message}</p>}
    </>}
    <ul className="connection-list">{graph.edges.map(e => <li key={e.id}><span><strong>{e.kind}</strong>
      <code>{e.source}.{e.sourcePort}<br />to {e.target}.{e.targetPort}</code></span>
      {!readOnly && <button className="quiet" aria-label={`Remove connection ${e.id}`} onClick={() => change({ ...graph, edges: graph.edges.filter(other => other.id !== e.id) })}>Remove</button>}
    </li>)}</ul>
  </details>;
}
