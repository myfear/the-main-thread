import { describe, expect, it } from 'vitest';
import { parseSnapshot, validateSnapshot } from '../../src/validation';
import { empty, quarkus, synthetic } from './fixtures';

describe('versioned snapshot imports', () => {
  it('validates both genuine analyzer exports and an empty snapshot', () => {
    expect(validateSnapshot(quarkus).types).toHaveLength(219);
    expect(parseSnapshot(JSON.stringify(synthetic)).types).toHaveLength(9);
    expect(validateSnapshot(empty()).types).toEqual([]);
  });

  it('explains malformed JSON and unsupported versions', () => {
    expect(() => parseSnapshot('{')).toThrow('not valid JSON');
    expect(() => validateSnapshot({ ...synthetic, schemaVersion: '2' })).toThrow('Unsupported schema version');
  });

  it('rejects missing data, negative counts, and unknown fields using the shared schema', () => {
    const negative = structuredClone(synthetic);
    negative.types[0]!.metrics.head.methodCount = -1;
    expect(() => validateSnapshot(negative)).toThrow('/methodCount');
    const missing = { ...structuredClone(synthetic), repository: {} };
    expect(() => validateSnapshot(missing)).toThrow('displayName');
    expect(() => validateSnapshot({ ...synthetic, extra: true })).toThrow('additional properties');
    const absent = structuredClone(synthetic) as unknown as { types: { metrics: { head: null } }[] };
    absent.types[0]!.metrics.head = null;
    expect(() => validateSnapshot(absent)).toThrow('Software City v1');
  });

  it('rejects duplicate and inconsistent identities', () => {
    const duplicate = structuredClone(synthetic);
    duplicate.types[1] = structuredClone(duplicate.types[0]!);
    expect(() => validateSnapshot(duplicate)).toThrow('Duplicate type ID');
    const inconsistent = structuredClone(synthetic);
    inconsistent.types[0]!.qualifiedName = 'Wrong';
    expect(() => validateSnapshot(inconsistent)).toThrow('Inconsistent identity');
  });

  it('rejects contradictory spans, summaries, and unsafe integer counts', () => {
    const span = structuredClone(synthetic);
    span.types[0]!.source.head.endLine++;
    expect(() => validateSnapshot(span)).toThrow('Source span');
    const summary = structuredClone(synthetic);
    summary.summary.typeCountHead++;
    expect(() => validateSnapshot(summary)).toThrow('summary.typeCountHead');
    const unsafe = structuredClone(synthetic);
    unsafe.types[0]!.metrics.head.methodCount = Number.MAX_SAFE_INTEGER + 1;
    expect(() => validateSnapshot(unsafe)).toThrow('exact integer range');
  });

  it('requires incomplete to agree with errors, while keeping informational notes usable', () => {
    const doc = structuredClone(synthetic);
    doc.diagnostics.push({ severity: 'info', code: 'COMPACT_SOURCE_SKIPPED', revision: 'head',
      path: 'src/main/java/Main.java', line: null, message: 'No named top-level type.', affectedTypeIds: [] });
    expect(validateSnapshot(doc).summary.incomplete).toBe(false);
    doc.diagnostics[0]!.severity = 'error';
    expect(() => validateSnapshot(doc)).toThrow('summary.incomplete');
    doc.summary.incomplete = true;
    expect(validateSnapshot(doc).summary.incomplete).toBe(true);
  });

  it('explains that a comparison export needs the later comparison viewer', () => {
    const comparison = structuredClone(synthetic) as unknown as Record<string, unknown>;
    comparison.mode = 'comparison';
    const revisions = comparison.revisions as { base: unknown; head: unknown };
    revisions.base = structuredClone(revisions.head);
    expect(() => validateSnapshot(comparison)).toThrow('analyze command');
  });
});
