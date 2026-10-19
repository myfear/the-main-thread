import rawSynthetic from '../../../fixtures/synthetic.snapshot.json';
import rawQuarkus from '../../../fixtures/quarkus-runtime.snapshot.json';
import { validateSnapshot } from '../../src/validation';
import type { Snapshot, SnapshotType } from '../../src/model';

export const synthetic = validateSnapshot(rawSynthetic);
export const quarkus = validateSnapshot(rawQuarkus);

export function type(module: string, pkg: string, name: string, fields = 0, methods = 0): SnapshotType {
  const template = synthetic.types[0]!;
  const qualifiedName = pkg ? pkg + '.' + name : name;
  return {
    ...structuredClone(template), module, packageName: pkg, qualifiedName, typeName: name,
    id: module + '::' + qualifiedName,
    metrics: { base: null, head: { ...template.metrics.head, fieldCount: fields, methodCount: methods } },
  };
}

export function empty(): Snapshot {
  return { ...structuredClone(synthetic), types: [],
    summary: { typeCountBase: 0, typeCountHead: 0, added: 0, removed: 0, modified: 0, unchanged: 0, incomplete: false } };
}
