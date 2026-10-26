import { describe, expect, it } from 'vitest';
import fixture from '../../backend/src/main/resources/studio/examples/pr-review-basic.graph.json';
import { canRefreshRun, connectionEdge, nodeStatus, parseGraph, removeNodes } from './model';
import type { Descriptor, Graph, Run, RunEvent } from './types';

const graph = fixture as Graph;
const descriptors: Descriptor[] = [
  { type: 'task.fetch-pr-fixture', version: 1, label: 'Load', executable: true,
    inputPorts: [{ id: 'fixture', schemaRef: 'fixture-id-v1', required: true }],
    outputPorts: [{ id: 'pullRequest', schemaRef: 'pull-request-v1', required: true }],
    controlInputs: ['in'], controlOutputs: ['next'], capabilityInputs: {}, capabilityOutput: null, configSchema: { properties: {} } },
  { type: 'ai.agent', version: 1, label: 'Review', executable: true,
    inputPorts: [{ id: 'pullRequest', schemaRef: 'pull-request-v1', required: true }],
    outputPorts: [{ id: 'reviewResult', schemaRef: 'review-result-v1', required: true }],
    controlInputs: ['in'], controlOutputs: ['next'], capabilityInputs: { model: { type: 'ai.model-ref', required: true } },
    capabilityOutput: null, configSchema: { properties: {} } },
];
const connection = { source: 'fetch', sourceHandle: 'data:pullRequest', target: 'review', targetHandle: 'data:pullRequest' };

describe('graph edits preserve the backend contract', () => {
  it('round-trips the canonical graph without React Flow fields', () => {
    expect(parseGraph(JSON.stringify(graph))).toEqual(graph);
  });
  it('rejects malformed imports before rendering', () => {
    for (const mutate of [
      (g: Graph) => { g.nodes[0].position.x = Infinity; },
      (g: Graph) => { g.nodes[1].id = g.nodes[0].id; },
      (g: Graph) => { g.edges[0].target = 'missing'; },
      (g: Graph) => { g.edges[1].id = g.edges[0].id; },
    ]) {
      const bad = structuredClone(graph); mutate(bad);
      expect(() => parseGraph(JSON.stringify(bad))).toThrow();
    }
  });
  it('removes dependent edges when a node is deleted', () => {
    const result = removeNodes(graph, ['review']);
    expect(result.nodes.some(n => n.id === 'review')).toBe(false);
    expect(result.edges.some(e => e.source === 'review' || e.target === 'review')).toBe(false);
    expect(result.edges.some(e => e.source === 'input' && e.target === 'fetch')).toBe(true);
  });
  it('accepts matching named ports and rejects duplicate binding', () => {
    expect(connectionEdge(connection, graph, descriptors)).toBeNull();
    const unbound = { ...graph, edges: graph.edges.filter(e => e.id !== 'e8') };
    expect(connectionEdge(connection, unbound, descriptors)).toMatchObject({ kind: 'data', sourcePort: 'pullRequest', targetPort: 'pullRequest' });
  });
  it('rejects mixed kinds, unknown handles, self edges, and schema mismatches', () => {
    const empty = { ...graph, edges: [] };
    expect(connectionEdge({ ...connection, targetHandle: 'control:in' }, empty, descriptors)).toBeNull();
    expect(connectionEdge({ ...connection, sourceHandle: 'data:unknown' }, empty, descriptors)).toBeNull();
    expect(connectionEdge({ ...connection, target: 'fetch' }, empty, descriptors)).toBeNull();
    const mismatch = structuredClone(descriptors); mismatch[1].inputPorts[0].schemaRef = 'boolean-v1';
    expect(connectionEdge(connection, empty, mismatch)).toBeNull();
  });
});

describe('execution states use only the run journal', () => {
  const node = graph.nodes.find(n => n.id === 'review')!;
  const event = (kind: string) => ({ nodeId: 'review', kind } as RunEvent);
  it('does not infer a skipped or successful branch from terminal status', () => {
    expect(nodeStatus(node, [], true, true)).toBe('No recorded task');
    expect(nodeStatus(node, [], false, true)).toBe('Awaiting events');
  });
  it('keeps capabilities out of executable task state', () => {
    expect(nodeStatus(node, [], true, false)).toBe('Configuration');
  });
  it('uses the last recorded node event and preserves a failure', () => {
    expect(nodeStatus(node, [event('TASK_STARTED')], false, true)).toBe('Started');
    expect(nodeStatus(node, [event('TASK_STARTED'), event('TASK_COMPLETED')], true, true)).toBe('Completed');
    expect(nodeStatus(node, [event('TASK_STARTED'), event('TASK_FAILED')], true, true)).toBe('Failed');
  });
  it('keeps observation available after a successful read of an uncertain start', () => {
    const run = { runId: '5d9ee24f-e7f5-4c45-948e-f868609ae14a', status: 'START_UNCERTAIN' } as Run;
    expect(canRefreshRun(run, '')).toBe(true);
    expect(canRefreshRun({ ...run, status: 'COMPLETED' }, '')).toBe(false);
    expect(canRefreshRun({ ...run, status: 'RUNNING' }, 'Read failed')).toBe(true);
    expect(canRefreshRun({ ...run, runId: 'Response unavailable' }, 'Read failed')).toBe(false);
  });
});
