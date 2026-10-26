import { memo, useState } from 'react';
import { Background, Controls, Handle, MarkerType, Position, ReactFlow,
  type Connection, type Edge, type Node, type NodeChange, type NodeProps } from '@xyflow/react';
import { connectionEdge, edgeColors, isTerminal, nodeStatus, ports } from './model';
import type { Descriptor, EdgeKind, Graph, GraphNode, Run } from './types';

type StudioNode = Node<{ node: GraphNode; descriptor?: Descriptor; status?: string; invalid: boolean }, 'studio'>;
const StudioNodeView = memo(function StudioNodeView({ data, selected }: NodeProps<StudioNode>) {
  const { node, descriptor, status, invalid } = data;
  const summary = node.config.minimumSeverity ? `Review at ${node.config.minimumSeverity} or above`
    : node.type === 'ai.agent' ? `Fixture · ${node.config.maxToolCalls} tool calls max`
    : descriptor?.capabilityOutput ? String(Object.values(node.config)[0] ?? 'Configuration')
    : node.type === 'workflow.output' ? 'Return the review decision'
    : node.type === 'logic.switch' ? 'Branch on a boolean input'
    : node.type === 'workflow.input' ? 'Selected PR fixture'
    : node.type === 'task.fetch-pr-fixture' ? 'Read bundled pull request' : 'Registered workflow node';
  return <div className={`studio-node ${selected ? 'selected' : ''} ${invalid ? 'invalid' : ''} ${status === 'Completed' ? 'completed' : ''} ${status === 'Failed' ? 'failed' : ''}`}>
    <div className="node-label">{descriptor?.label ?? node.type}</div>
    <div className="node-summary">{summary}</div>
    <div className="node-foot"><code>{node.id}</code><span>{status ?? (descriptor?.executable ? 'Task' : 'Capability')}</span></div>
    {descriptor && (['target', 'source'] as const).flatMap(direction => ports(descriptor, direction).map((p, i) =>
      <Handle key={`${direction}:${p.kind}:${p.id}`} type={direction} id={`${p.kind}:${p.id}`}
        position={direction === 'target' ? Position.Left : Position.Right}
        style={{ top: 30 + i * 16, background: edgeColors[p.kind] }}
        title={`${direction === 'target' ? 'Input' : 'Output'} ${p.kind}: ${p.id}`}
        aria-label={`${node.id} ${direction} ${p.kind} ${p.id}`} />))}
  </div>;
});
const nodeTypes = { studio: StudioNodeView };

export function GraphCanvas({ graph, catalog, selected, select, change, layers, readOnly, run, invalidNodes }: {
  graph: Graph; catalog: Descriptor[]; selected: string | null; select: (id: string | null) => void;
  change: (graph: Graph) => void; layers: EdgeKind[]; readOnly: boolean; run?: Run; invalidNodes: string[];
}) {
  // Measurements belong to React Flow, not to the canonical graph. Preserve
  // them across event/selection updates so controlled nodes stay visible.
  const [measurements, setMeasurements] = useState<Record<string, { width: number; height: number }>>({});
  const nodes: StudioNode[] = graph.nodes.map(n => {
    const descriptor = catalog.find(d => d.type === n.type);
    return { id: n.id, type: 'studio', position: n.position, selected: n.id === selected,
      measured: measurements[n.id], initialWidth: 220, initialHeight: 122,
      ariaLabel: `${descriptor?.label ?? n.type}: ${n.id}`, data: { node: n, descriptor,
        invalid: invalidNodes.includes(n.id),
        status: run ? nodeStatus(n, run.events, isTerminal(run), descriptor?.executable ?? true) : undefined } };
  });
  const edges: Edge[] = graph.edges.map(e => ({ id: e.id, source: e.source, target: e.target,
    sourceHandle: `${e.kind}:${e.sourcePort}`, targetHandle: `${e.kind}:${e.targetPort}`,
    hidden: !layers.includes(e.kind), type: 'smoothstep',
    label: e.kind === 'control' && ['true', 'false'].includes(e.sourcePort) ? e.sourcePort : undefined,
    ariaLabel: `${e.kind}: ${e.source}.${e.sourcePort} to ${e.target}.${e.targetPort}`,
    style: { stroke: edgeColors[e.kind], strokeWidth: e.kind === 'control' ? 2 : 1.5,
      strokeDasharray: e.kind === 'data' ? '4 4' : e.kind === 'capability' ? '9 4' : undefined },
    markerEnd: { type: MarkerType.ArrowClosed, color: edgeColors[e.kind], width: 16, height: 16 },
  }));
  function onNodesChange(changes: NodeChange<StudioNode>[]) {
    const dimensions = changes.filter(c => c.type === 'dimensions').filter(c => c.dimensions);
    if (dimensions.length) setMeasurements(current => {
      const changed = dimensions.filter(c => current[c.id]?.width !== c.dimensions!.width || current[c.id]?.height !== c.dimensions!.height);
      return changed.length ? { ...current, ...Object.fromEntries(changed.map(c => [c.id, c.dimensions!])) } : current;
    });
    for (const item of changes) if (item.type === 'select' && item.selected) select(item.id);
    if (readOnly) return;
    const positions = changes.filter(c => c.type === 'position');
    const removed = changes.filter(c => c.type === 'remove').map(c => c.id);
    if (!positions.length && !removed.length) return;
    change({ ...graph, nodes: graph.nodes.filter(n => !removed.includes(n.id)).map(n => {
      const p = positions.find(c => c.id === n.id);
      return p?.position ? { ...n, position: p.position } : n;
    }), edges: graph.edges.filter(e => !removed.includes(e.source) && !removed.includes(e.target)) });
  }
  function onConnect(connection: Connection) {
    const edge = connectionEdge(connection, graph, catalog);
    if (edge && !readOnly) change({ ...graph, edges: [...graph.edges, edge] });
  }
  return <div className="graph-canvas" aria-label={run ? 'Published execution graph' : 'Editable workflow graph'}>
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} onNodesChange={onNodesChange}
      onEdgesChange={changes => { const removed = changes.filter(c => c.type === 'remove').map(c => c.id);
        if (!readOnly && removed.length) change({ ...graph, edges: graph.edges.filter(e => !removed.includes(e.id)) }); }}
      onNodeClick={(_, node) => select(node.id)} onPaneClick={() => select(null)} onConnect={onConnect}
      isValidConnection={c => !!connectionEdge(c as Connection, graph, catalog)}
      nodesDraggable={!readOnly} nodesConnectable={!readOnly} edgesReconnectable={false}
      zoomOnScroll={false} preventScrolling={false}
      deleteKeyCode={readOnly ? null : ['Backspace', 'Delete']} fitView fitViewOptions={{ padding: 0.1, minZoom: 0.65, maxZoom: 1 }}
      minZoom={0.35} maxZoom={1.6}>
      <Background color="#d8d6e2" gap={24} size={1} />
      <Controls showInteractive={false} fitViewOptions={{ padding: 0.1, minZoom: 0.65, maxZoom: 1 }} />
    </ReactFlow>
  </div>;
}
