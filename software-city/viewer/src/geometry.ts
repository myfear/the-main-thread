import * as THREE from 'three';
import type { CityLayout } from './layout';
import { methodHeight, type ColorMetric, type SnapshotType } from './model';

export interface BuildingInstances {
  mesh: THREE.InstancedMesh;
  ids: readonly string[];
}

export function createBuildings(layout: CityLayout, types: readonly SnapshotType[]): BuildingInstances {
  const byId = new Map(types.map(type => [type.id, type]));
  const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1),
    new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.8 }),
    Math.max(1, layout.buildings.length));
  mesh.count = layout.buildings.length;
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  const transform = new THREE.Object3D();
  for (const [index, placement] of layout.buildings.entries()) {
    const type = byId.get(placement.id);
    if (!type) throw new Error('Layout contains an unknown type ID.');
    const height = methodHeight(type.metrics.head.methodCount);
    transform.position.set(placement.x, 0.04 + height / 2, placement.z);
    transform.scale.set(placement.width, height, placement.depth);
    transform.updateMatrix();
    mesh.setMatrixAt(index, transform.matrix);
  }
  mesh.instanceMatrix.needsUpdate = true;
  mesh.computeBoundingBox();
  mesh.computeBoundingSphere();
  return { mesh, ids: Object.freeze(layout.buildings.map(building => building.id)) };
}

export const LOW_COLOR = '#b4bfe9';
export const HIGH_COLOR = '#2d2786';

export function colorBuildings(instances: BuildingInstances, types: ReadonlyMap<string, SnapshotType>,
    metric: ColorMetric, matches: ReadonlySet<string> | null): number {
  let maximum = 0;
  for (const type of types.values()) maximum = Math.max(maximum, type.metrics.head[metric]);
  const low = new THREE.Color(LOW_COLOR);
  const high = new THREE.Color(HIGH_COLOR);
  const muted = new THREE.Color('#dedce5');
  for (const [index, id] of instances.ids.entries()) {
    const type = types.get(id)!;
    const value = maximum === 0 ? 0 : Math.log1p(type.metrics.head[metric]) / Math.log1p(maximum);
    instances.mesh.setColorAt(index, matches && !matches.has(id) ? muted : low.clone().lerp(high, value));
  }
  if (instances.mesh.instanceColor) instances.mesh.instanceColor.needsUpdate = true;
  return maximum;
}

export function createDistrictOutlines(layout: CityLayout): THREE.LineSegments {
  const points: number[] = [];
  const colors: number[] = [];
  for (const district of layout.districts) {
    const x0 = district.x - district.width / 2;
    const x1 = district.x + district.width / 2;
    const z0 = district.z - district.depth / 2;
    const z1 = district.z + district.depth / 2;
    const y = 0.015 + district.level * 0.002;
    points.push(x0, y, z0, x1, y, z0, x1, y, z0, x1, y, z1,
      x1, y, z1, x0, y, z1, x0, y, z1, x0, y, z0);
    const color = new THREE.Color(district.kind === 'module' ? '#70689b' : '#b7b2c4');
    for (let index = 0; index < 8; index++) colors.push(color.r, color.g, color.b);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(points, 3));
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  return new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ vertexColors: true }));
}
