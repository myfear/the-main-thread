import type { CityDocument } from './generated/city-document';

export type CityType = CityDocument['types'][number];
export type Metrics = NonNullable<CityType['metrics']['head']>;
export type Source = NonNullable<CityType['source']['head']>;
export type TypeKind = NonNullable<CityType['typeKind']['head']>;
export type SnapshotType = CityType & {
  metrics: { base: null; head: Metrics };
  source: { base: null; head: Source };
  typeKind: { base: null; head: TypeKind };
};
export type Snapshot = Omit<CityDocument, 'mode' | 'types'> & {
  mode: 'snapshot';
  types: SnapshotType[];
};
export type ColorMetric = 'methodCount' | 'fieldCount' | 'sourceLines';

export function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function searchTypes(types: readonly SnapshotType[], query: string): SnapshotType[] {
  const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return types.filter(type => {
    const haystack = [type.typeName, type.qualifiedName, type.packageName, type.module].join(' ').toLowerCase();
    return terms.every(term => haystack.includes(term));
  }).sort((a, b) => compareText(a.qualifiedName, b.qualifiedName) || compareText(a.id, b.id));
}

export function methodHeight(methodCount: number): number {
  return 1 + 2 * Math.log2(1 + methodCount);
}

export function sourceLocation(type: SnapshotType): string {
  return type.source.head.path + ':' + type.source.head.startLine;
}
