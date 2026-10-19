import { compareText, type CityType } from './model';

export interface Rectangle {
  readonly x: number;
  readonly z: number;
  readonly width: number;
  readonly depth: number;
}
export interface Building extends Rectangle { readonly id: string }
export interface District extends Rectangle {
  readonly id: string;
  readonly module: string;
  readonly packageName: string;
  readonly kind: 'module' | 'package';
  readonly level: number;
  readonly ownsTypes: boolean;
}
export interface CityLayout {
  readonly buildings: readonly Building[];
  readonly districts: readonly District[];
  readonly bounds: Rectangle;
}
interface Node {
  key: string;
  kind: 'root' | 'module' | 'package' | 'type';
  module: string;
  packageName: string;
  weight: number;
  children: Map<string, Node>;
}
interface Region { left: number; top: number; width: number; depth: number }

function node(key: string, kind: Node['kind'], module: string, packageName: string): Node {
  return { key, kind, module, packageName, weight: 0, children: new Map() };
}
function child(parent: Node, key: string, kind: Node['kind'], module: string, packageName: string): Node {
  let result = parent.children.get(key);
  if (!result) {
    result = node(key, kind, module, packageName);
    parent.children.set(key, result);
  }
  return result;
}
function inset(region: Region, padding: number): Region {
  return { left: region.left + padding, top: region.top + padding,
    width: region.width - padding * 2, depth: region.depth - padding * 2 };
}
function rectangle(region: Region): Rectangle {
  return { x: region.left + region.width / 2, z: region.top + region.depth / 2,
    width: region.width, depth: region.depth };
}

/** Pure weighted binary treemap. X/Z are centers. Returned placements are immutable. */
export function layoutCity(types: readonly CityType[]): CityLayout {
  if (types.length === 0) {
    return Object.freeze({ buildings: Object.freeze([]), districts: Object.freeze([]),
      bounds: Object.freeze({ x: 0, z: 0, width: 0, depth: 0 }) });
  }
  const root = node('root', 'root', '', '');
  for (const type of [...types].sort((a, b) => compareText(a.id, b.id))) {
    let parent = child(root, 'm:' + type.module, 'module', type.module, '');
    const segments = type.packageName ? type.packageName.split('.') : [''];
    let packageName = '';
    for (const segment of segments) {
      packageName = packageName ? packageName + '.' + segment : segment;
      parent = child(parent, 'p:' + type.module + '::' + packageName, 'package', type.module, packageName);
    }
    child(parent, type.id, 'type', type.module, type.packageName).weight =
      1 + Math.min(63, Math.max(type.metrics.base?.fieldCount ?? 0, type.metrics.head?.fieldCount ?? 0));
  }
  function weigh(current: Node): number {
    if (current.kind !== 'type') {
      current.weight = [...current.children.values()].reduce((total, item) => total + weigh(item), 0);
    }
    return current.weight;
  }
  weigh(root);
  const size = 4 * Math.sqrt(root.weight);
  const buildings: Building[] = [];
  const districts: District[] = [];

  function place(current: Node, region: Region, level: number): void {
    if (current.kind === 'type') {
      const padding = Math.min(0.32, Math.min(region.width, region.depth) * 0.12);
      buildings.push(Object.freeze({ id: current.key, ...rectangle(inset(region, padding)) }));
      return;
    }
    if (current.kind !== 'root') {
      districts.push(Object.freeze({ id: current.key, kind: current.kind,
        module: current.module, packageName: current.packageName, level,
        ownsTypes: [...current.children.values()].some(item => item.kind === 'type'),
        ...rectangle(region) }));
    }
    const padding = current.kind === 'root' ? 0 : Math.min(0.9, Math.min(region.width, region.depth) * 0.06);
    pack([...current.children.values()].sort((a, b) => compareText(a.key, b.key)),
      inset(region, padding), level + 1);
  }
  function pack(items: Node[], region: Region, level: number): void {
    if (items.length === 1) {
      place(items[0]!, region, level);
      return;
    }
    const total = items.reduce((sum, item) => sum + item.weight, 0);
    let split = 1;
    let leftWeight = items[0]!.weight;
    let bestDistance = Math.abs(total / 2 - leftWeight);
    let cumulative = leftWeight;
    for (let index = 1; index < items.length - 1; index++) {
      cumulative += items[index]!.weight;
      const distance = Math.abs(total / 2 - cumulative);
      if (distance < bestDistance) {
        split = index + 1;
        leftWeight = cumulative;
        bestDistance = distance;
      }
    }
    const fraction = leftWeight / total;
    if (region.width >= region.depth) {
      const width = region.width * fraction;
      pack(items.slice(0, split), { ...region, width }, level);
      pack(items.slice(split), { ...region, left: region.left + width, width: region.width - width }, level);
    } else {
      const depth = region.depth * fraction;
      pack(items.slice(0, split), { ...region, depth }, level);
      pack(items.slice(split), { ...region, top: region.top + depth, depth: region.depth - depth }, level);
    }
  }
  place(root, { left: -size / 2, top: -size / 2, width: size, depth: size }, 0);
  buildings.sort((a, b) => compareText(a.id, b.id));
  districts.sort((a, b) => compareText(a.id, b.id));
  return Object.freeze({ buildings: Object.freeze(buildings), districts: Object.freeze(districts),
    bounds: Object.freeze({ x: 0, z: 0, width: size, depth: size }) });
}
