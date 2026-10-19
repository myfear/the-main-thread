import { describe, expect, it } from 'vitest';
import { layoutCity, type CityLayout } from '../../src/layout';
import { quarkus, synthetic, type } from './fixtures';

function verifyRectangles(layout: CityLayout): void {
  for (const building of layout.buildings) {
    expect([building.x, building.z, building.width, building.depth].every(Number.isFinite)).toBe(true);
    expect(building.width).toBeGreaterThan(0);
    expect(building.depth).toBeGreaterThan(0);
    expect(Math.abs(building.x) + building.width / 2).toBeLessThanOrEqual(layout.bounds.width / 2 + 1e-8);
    expect(Math.abs(building.z) + building.depth / 2).toBeLessThanOrEqual(layout.bounds.depth / 2 + 1e-8);
  }
  for (let a = 0; a < layout.buildings.length; a++) {
    for (let b = a + 1; b < layout.buildings.length; b++) {
      const first = layout.buildings[a]!;
      const second = layout.buildings[b]!;
      expect(Math.abs(first.x - second.x) * 2 >= first.width + second.width - 1e-8
        || Math.abs(first.z - second.z) * 2 >= first.depth + second.depth - 1e-8).toBe(true);
    }
  }
}

describe('deterministic hierarchical layout', () => {
  it('produces a stable golden layout for the reproducible fixture', () => {
    const layout = layoutCity(synthetic.types);
    expect(layout.buildings.map(building => Object.fromEntries(Object.entries(building)
      .map(([key, value]) => [key, typeof value === 'number' ? Number(value.toFixed(6)) : value])))).toMatchSnapshot();
  });

  it('is unchanged by input ordering and never mutates the input', () => {
    const before = JSON.stringify(quarkus);
    const reversed = [...quarkus.types].reverse();
    const interleaved = [...quarkus.types.filter((_, index) => index % 2), ...quarkus.types.filter((_, index) => !(index % 2))];
    expect(layoutCity(reversed)).toEqual(layoutCity(quarkus.types));
    expect(layoutCity(interleaved)).toEqual(layoutCity(quarkus.types));
    expect(JSON.stringify(quarkus)).toEqual(before);
    expect(Object.isFrozen(layoutCity(quarkus.types).buildings[0])).toBe(true);
  });

  it('keeps all real Quarkus and synthetic rectangles collision-free', () => {
    verifyRectangles(layoutCity(quarkus.types));
    verifyRectangles(layoutCity(synthetic.types));
  });

  it('handles fieldless types, nested packages, shared module/package names, and capped field counts', () => {
    const types = [type('a', 'p', 'Shared'), type('b', 'p', 'Shared'), type('a', 'p.child', 'Nested', 4),
      ...Array.from({ length: 30 }, (_, index) => type('a', '', 'Empty' + index))];
    const layout = layoutCity(types);
    verifyRectangles(layout);
    expect(layout.buildings).toHaveLength(33);
    expect(layout.districts.filter(district => district.packageName === 'p')).toHaveLength(2);
    expect(layoutCity([type('.', 'p', 'Large', 63)])).toEqual(layoutCity([type('.', 'p', 'Large', 1000)]));
  });

  it('handles empty input explicitly', () => {
    expect(layoutCity([])).toEqual({ buildings: [], districts: [], bounds: { x: 0, z: 0, width: 0, depth: 0 } });
  });
});
