/* Generated from software-city-v1.schema.json. Do not edit.
 * Schema SHA-256: f59f2505d99ace5712ba02bfa23a3105a477592d092dc6d7a83cb9aed4b5c5fe
 */

/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "path".
 */
export type Path = string;
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "kind".
 */
export type Kind = 'class' | 'interface' | 'enum' | 'record' | 'annotation';

export interface CityDocument {
  schemaVersion: '1';
  mode: 'snapshot' | 'comparison';
  repository: {
    displayName: string;
  };
  revisions: {
    base: null | Revision;
    head: Revision;
  };
  types: Type[];
  diagnostics: Diagnostic[];
  summary: Summary;
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "revision".
 */
export interface Revision {
  ref: string;
  commit: string;
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "type".
 */
export interface Type {
  id: string;
  module: Path;
  packageName: string;
  qualifiedName: string;
  typeName: string;
  typeKind: {
    base: null | Kind;
    head: null | Kind;
  };
  source: {
    base: null | Source;
    head: null | Source;
  };
  metrics: {
    base: null | Metrics;
    head: null | Metrics;
  };
  change: {
    status: 'added' | 'removed' | 'modified' | 'unchanged';
    fileChanged: boolean;
  };
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "source".
 */
export interface Source {
  path: Path;
  startLine: number;
  endLine: number;
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "metrics".
 */
export interface Metrics {
  methodCount: number;
  constructorCount: number;
  fieldCount: number;
  sourceLines: number;
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "diagnostic".
 */
export interface Diagnostic {
  severity: 'info' | 'warning' | 'error';
  code: string;
  revision: 'base' | 'head';
  path: Path;
  line: null | number;
  message: string;
  affectedTypeIds: string[];
}
/**
 * This interface was referenced by `CityDocument`'s JSON-Schema
 * via the `definition` "summary".
 */
export interface Summary {
  typeCountBase: number;
  typeCountHead: number;
  added: number;
  removed: number;
  modified: number;
  unchanged: number;
  incomplete: boolean;
}
