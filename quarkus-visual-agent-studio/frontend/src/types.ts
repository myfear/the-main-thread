export type EdgeKind = 'control' | 'data' | 'capability';
export type Config = Record<string, unknown>;
export interface GraphNode {
  id: string; type: string; version: number; position: { x: number; y: number }; config: Config;
}
export interface GraphEdge {
  id: string; kind: EdgeKind; source: string; sourcePort: string; target: string; targetPort: string;
}
export interface Graph {
  schemaVersion: number; flowId: string; name: string; description: string;
  nodes: GraphNode[]; edges: GraphEdge[]; metadata?: unknown;
}
export interface SchemaProperty {
  type?: string; const?: unknown; enum?: string[]; minimum?: number; maximum?: number;
  items?: { const?: unknown }; minItems?: number;
}
export interface Descriptor {
  type: string; version: number; label: string; executable: boolean;
  inputPorts: { id: string; schemaRef: string; required: boolean }[];
  outputPorts: { id: string; schemaRef: string; required: boolean }[];
  controlInputs: string[]; controlOutputs: string[];
  capabilityInputs: Record<string, { type: string; required: boolean }>;
  capabilityOutput: string | null;
  configSchema: { properties: Record<string, SchemaProperty> };
}
export interface Diagnostic { code: string; nodeId: string | null; edgeId: string | null; path: string; message: string }
export interface Validation { valid: boolean; errors: Diagnostic[] }
export interface Publication {
  publicationId: string; flowId: string; version: string; status: 'PUBLISHING' | 'READY' | 'FAILED_TO_DEPLOY';
  graphSha256: string; artifactSha256: string; compilerVersion: string;
}
export interface Compiled { version: string; yaml: string; graphJson: string; manifest: Record<string, unknown>; artifactSha256: string }
export interface RunEvent {
  eventId: number; kind: string; taskId: string | null; nodeId: string | null;
  runId: string; version: string; occurredAt: string; error?: unknown;
}
export interface Run {
  runId: string; publicationId: string; instanceId?: string; version: string; status: string;
  events: RunEvent[]; lostEvents: number;
  output: null | {
    decision: string; requiresApproval: boolean; modelMode: string;
    reviewResult: { summary: string; findings: { file: string; line: number; severity: string; category: string; message: string }[] };
  };
}
