import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { colorBuildings, createBuildings, createDistrictOutlines } from '../../src/geometry';
import { layoutCity } from '../../src/layout';
import { methodHeight, searchTypes, sourceLocation } from '../../src/model';
import { quarkus, synthetic } from './fixtures';

describe('instanced geometry and data interaction', () => {
  it('uses one instanced mesh and preserves ID-to-instance mapping for actual ray hits', () => {
    const layout = layoutCity(synthetic.types);
    const instances = createBuildings(layout, synthetic.types);
    expect(instances.mesh.isInstancedMesh).toBe(true);
    expect(instances.mesh.count).toBe(9);
    instances.mesh.updateMatrixWorld(true);
    const size = layout.bounds.width;
    const camera = new THREE.OrthographicCamera(-size, size, size, -size, 0.1, 500);
    camera.position.set(0, 100, 0);
    camera.up.set(0, 0, -1);
    camera.lookAt(0, 0, 0);
    camera.updateMatrixWorld(true);
    const raycaster = new THREE.Raycaster();
    for (const building of layout.buildings) {
      const position = new THREE.Vector3(building.x, 0, building.z).project(camera);
      raycaster.setFromCamera(new THREE.Vector2(position.x, position.y), camera);
      const hit = raycaster.intersectObject(instances.mesh, false)[0];
      expect(hit?.instanceId).toBeDefined();
      expect(instances.ids[hit!.instanceId!]).toBe(building.id);
    }
  });

  it('recolors and dims without changing any placement matrix', () => {
    const instances = createBuildings(layoutCity(quarkus.types), quarkus.types);
    const before = instances.mesh.instanceMatrix.array.slice();
    const byId = new Map(quarkus.types.map(type => [type.id, type]));
    colorBuildings(instances, byId, 'fieldCount', new Set([quarkus.types[0]!.id]));
    colorBuildings(instances, byId, 'sourceLines', null);
    expect(instances.mesh.instanceMatrix.array).toEqual(before);
    expect(instances.mesh.instanceColor).not.toBeNull();
  });

  it('batches district outlines and keeps zero-method buildings visible', () => {
    const layout = layoutCity(synthetic.types);
    const outlines = createDistrictOutlines(layout);
    expect(outlines.isLineSegments).toBe(true);
    expect(outlines.geometry.getAttribute('position').count).toBe(layout.districts.length * 8);
    expect(methodHeight(0)).toBe(1);
    expect(methodHeight(2)).toBeGreaterThan(methodHeight(1));
  });

  it('searches type/package/module with stable ordering and exposes exact source locations', () => {
    expect(searchTypes(synthetic.types, 'shared')).toHaveLength(2);
    expect(searchTypes(synthetic.types, 'app shared').map(type => type.module)).toEqual(['app']);
    expect(searchTypes(quarkus.types, 'CONFIGDIAGNOSTIC')[0]?.metrics.head.methodCount).toBe(17);
    expect(sourceLocation(searchTypes(quarkus.types, 'ConfigDiagnostic')[0]!))
      .toBe('core/runtime/src/main/java/io/quarkus/runtime/configuration/ConfigDiagnostic.java:36');
    expect(searchTypes(synthetic.types, 'not-a-type')).toEqual([]);
  });
});
