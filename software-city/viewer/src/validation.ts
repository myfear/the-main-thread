import Ajv2020 from 'ajv/dist/2020';
import schema from '../../schemas/software-city-v1.schema.json';
import type { CityDocument } from './generated/city-document';
import type { Snapshot } from './model';

const ajv = new Ajv2020({ allErrors: true, strict: true, strictTypes: false });
const isDocument = ajv.compile<CityDocument>(schema);

export class SnapshotError extends Error {}

export function parseSnapshot(text: string): Snapshot {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw new SnapshotError('This file is not valid JSON. Choose a JSON export from the analyzer.');
  }
  return validateSnapshot(value);
}

export function validateSnapshot(value: unknown): Snapshot {
  if (typeof value === 'object' && value !== null && 'schemaVersion' in value
      && typeof value.schemaVersion === 'string' && value.schemaVersion !== '1') {
    throw new SnapshotError('Unsupported schema version "' + value.schemaVersion + '". Choose a Software City v1 export.');
  }
  if (!isDocument(value)) {
    const messages = (isDocument.errors ?? []).slice(0, 4).map(error => {
      const missing = error.keyword === 'required' ? '/' + String(error.params.missingProperty) : '';
      return ((error.instancePath || '') + missing + ' ' + (error.message ?? 'is invalid')).trim();
    });
    throw new SnapshotError('The export does not match Software City v1: ' + messages.join('; ') + '.');
  }
  if (value.mode !== 'snapshot') {
    throw new SnapshotError('This viewer opens snapshot exports. Choose JSON created with the analyze command.');
  }
  const document = value as Snapshot;
  const ids = new Set<string>();
  for (const type of document.types) {
    if (ids.has(type.id)) {
      throw new SnapshotError('Duplicate type ID: ' + type.id + '. Export a fresh snapshot.');
    }
    ids.add(type.id);
    const qualifiedName = type.packageName ? type.packageName + '.' + type.typeName : type.typeName;
    if (type.id !== type.module + '::' + type.qualifiedName || type.qualifiedName !== qualifiedName) {
      throw new SnapshotError('Inconsistent identity for ' + type.id + '. Export a fresh snapshot.');
    }
    const source = type.source.head;
    const metrics = type.metrics.head;
    if (![source.startLine, source.endLine, ...Object.values(metrics)].every(Number.isSafeInteger)) {
      throw new SnapshotError('Counts or source lines for ' + type.id + ' exceed the exact integer range.');
    }
    if (source.endLine < source.startLine
        || metrics.sourceLines !== source.endLine - source.startLine + 1) {
      throw new SnapshotError('Source span and sourceLines disagree for ' + type.id + '.');
    }
  }
  const expected = {
    typeCountBase: 0, typeCountHead: document.types.length,
    added: 0, removed: 0, modified: 0, unchanged: document.types.length,
    incomplete: document.diagnostics.some(diagnostic => diagnostic.severity === 'error'),
  };
  for (const key of Object.keys(expected) as (keyof typeof expected)[]) {
    if (document.summary[key] !== expected[key]) {
      throw new SnapshotError('summary.' + key + ' does not match the included types and diagnostics.');
    }
  }
  return document;
}
