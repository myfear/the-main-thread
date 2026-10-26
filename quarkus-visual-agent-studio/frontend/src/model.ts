import type { Connection } from '@xyflow/react';
import type { Descriptor, EdgeKind, Graph, GraphEdge, GraphNode, Run, RunEvent } from './types';

export const kinds: EdgeKind[] = ['control', 'data', 'capability'];
export const edgeColors = { control: '#2d2786', data: '#226447', capability: '#805413' };
export function ports(d: Descriptor, direction: 'source' | 'target') {
  if (direction === 'source') return [
    ...d.controlOutputs.map(id => ({ kind: 'control' as const, id })),
    ...d.outputPorts.map(p => ({ kind: 'data' as const, id: p.id })),
    ...(d.capabilityOutput ? [{ kind: 'capability' as const, id: d.capabilityOutput }] : []),
  ];
  return [
    ...d.controlInputs.map(id => ({ kind: 'control' as const, id })),
    ...d.inputPorts.map(p => ({ kind: 'data' as const, id: p.id })),
    ...Object.keys(d.capabilityInputs).sort().map(id => ({ kind: 'capability' as const, id })),
  ];
}
export function connectionEdge(connection: Connection, graph: Graph, catalog: Descriptor[]): GraphEdge | null {
  const [kind, sourcePort] = (connection.sourceHandle ?? '').split(':');
  const [targetKind, targetPort] = (connection.targetHandle ?? '').split(':');
  if (!kinds.includes(kind as EdgeKind) || kind !== targetKind || !sourcePort || !targetPort) return null;
  if (connection.source === connection.target) return null;
  const sourceNode = graph.nodes.find(n => n.id === connection.source);
  const targetNode = graph.nodes.find(n => n.id === connection.target);
  const source = catalog.find(d => d.type === sourceNode?.type);
  const target = catalog.find(d => d.type === targetNode?.type);
  if (!source || !target || !ports(source, 'source').some(p => p.kind === kind && p.id === sourcePort)
    || !ports(target, 'target').some(p => p.kind === kind && p.id === targetPort)) return null;
  if (kind === 'data' && source.outputPorts.find(p => p.id === sourcePort)?.schemaRef
    !== target.inputPorts.find(p => p.id === targetPort)?.schemaRef) return null;
  if (kind === 'capability' && target.capabilityInputs[targetPort]?.type !== source.type) return null;
  if (graph.edges.some(e => e.kind === kind && e.target === connection.target && e.targetPort === targetPort)) return null;
  if (kind === 'control' && graph.edges.some(e => e.kind === kind && e.source === connection.source && e.sourcePort === sourcePort)) return null;
  return { id: `e_${crypto.randomUUID().slice(0, 8)}`, kind: kind as EdgeKind,
    source: connection.source, target: connection.target, sourcePort, targetPort };
}
export function defaultConfig(d: Descriptor) {
  return Object.fromEntries(Object.entries(d.configSchema.properties).map(([key, property]) => [key,
    property.const ?? property.enum?.[0] ?? (property.type === 'integer' ? property.minimum ?? 1
      : property.type === 'array' ? [property.items?.const] : '')]));
}
export function removeNodes(graph: Graph, ids: string[]): Graph {
  return { ...graph, nodes: graph.nodes.filter(n => !ids.includes(n.id)),
    edges: graph.edges.filter(e => !ids.includes(e.source) && !ids.includes(e.target)) };
}
export function nodeStatus(node: GraphNode, events: RunEvent[], terminal: boolean, executable: boolean) {
  if (!executable) return 'Configuration';
  const last = events.filter(e => e.nodeId === node.id).at(-1);
  if (last?.kind === 'TASK_COMPLETED') return 'Completed';
  if (last?.kind === 'TASK_FAILED') return 'Failed';
  if (last?.kind === 'TASK_STARTED') return 'Started';
  return terminal ? 'No recorded task' : 'Awaiting events';
}
export function isTerminal(run: Run) { return ['COMPLETED', 'FAILED', 'START_UNCERTAIN'].includes(run.status); }
export function canRefreshRun(run: Run, pollError: string) {
  return run.runId !== 'Response unavailable' && (!!pollError || run.status === 'START_UNCERTAIN');
}

// Import checks protect the editor from malformed shapes. The backend remains
// authoritative for semantic validity, unknown node types, and config schemas.
export function parseGraph(text: string): Graph {
  if (new TextEncoder().encode(text).length > 60_000) throw new Error('Graph files must be smaller than 60 KB.');
  const g = JSON.parse(text);
  if (!g || g.schemaVersion !== 1 || typeof g.flowId !== 'string' || typeof g.name !== 'string'
    || typeof g.description !== 'string' || !Array.isArray(g.nodes) || !Array.isArray(g.edges)
    || g.nodes.length > 32 || g.edges.length > 64) throw new Error('Expected a schema version 1 graph (up to 32 nodes and 64 connections).');
  const ids = new Set();
  for (const n of g.nodes) {
    if (!n || typeof n.id !== 'string' || ids.has(n.id) || typeof n.type !== 'string' || !Number.isInteger(n.version)
      || !Number.isFinite(n.position?.x) || !Number.isFinite(n.position?.y) || !n.config
      || typeof n.config !== 'object' || Array.isArray(n.config)) throw new Error('Each node needs a unique ID, type, version, position, and configuration object.');
    ids.add(n.id);
  }
  const edgeIds = new Set();
  for (const e of g.edges) {
    if (!e || typeof e.id !== 'string' || edgeIds.has(e.id) || !kinds.includes(e.kind)
      || !['source', 'sourcePort', 'target', 'targetPort'].every(k => typeof e[k] === 'string')
      || !ids.has(e.source) || !ids.has(e.target)) throw new Error('Each connection needs a unique ID, known nodes, a kind, and named ports.');
    edgeIds.add(e.id);
  }
  return g;
}
export function exampleLayout(graph: Graph): Graph {
  const positions: Record<string, [number, number]> = {
    input: [60, 30], fetch: [345, 30], review: [630, 30],
    policy: [630, 230], route: [345, 230], lowOutput: [60, 195], highOutput: [60, 345],
    model: [60, 530], prompt: [345, 530], tools: [630, 530],
  };
  return { ...graph, flowId: crypto.randomUUID(), nodes: graph.nodes.map(n => ({ ...n,
    position: positions[n.id] ? { x: positions[n.id][0], y: positions[n.id][1] } : n.position })) };
}
