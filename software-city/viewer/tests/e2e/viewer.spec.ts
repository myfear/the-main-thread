import { readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import type { CityDocument } from '../../src/generated/city-document';

const synthetic: CityDocument = JSON.parse(readFileSync(new URL('../../../fixtures/synthetic.snapshot.json', import.meta.url), 'utf8'));
const errors = new WeakMap<Page, string[]>();
const remoteRequests = new WeakMap<Page, string[]>();

test.beforeEach(async ({ page }) => {
  const failures: string[] = [];
  const remote: string[] = [];
  errors.set(page, failures);
  remoteRequests.set(page, remote);
  page.on('pageerror', error => failures.push(error.message));
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    if (url.hostname === '127.0.0.1') return route.continue();
    remote.push(url.href);
    return route.abort();
  });
});

test.afterEach(async ({ page }) => {
  expect(errors.get(page)).toEqual([]);
  expect(remoteRequests.get(page)).toEqual([]);
});

async function open(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.locator('#canvas-host')).toHaveAttribute('data-state', 'ready');
  await expect(page.locator('#result-count')).toHaveText('219');
}

async function importDocument(page: Page, document: unknown, name = 'snapshot.json'): Promise<void> {
  await page.locator('#file-input').setInputFiles({
    name, mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(document)),
  });
}

test('opens the real Quarkus snapshot and inspects exact metrics from the keyboard', async ({ page, context }) => {
  await open(page);
  await expect(page.locator('#repository-name')).toHaveText('quarkus');
  await expect(page.locator('#snapshot-summary')).toContainText('19 packages');
  await expect(page.getByRole('button', { name: 'Focus selected' })).toBeDisabled();
  const search = page.getByRole('searchbox', { name: 'Find a type' });
  await search.fill('ConfigDiagnostic');
  await expect(page.locator('#type-list button')).toHaveCount(1);
  await search.press('ArrowDown');
  await expect(page.locator('#type-list button')).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('#selected-name')).toHaveText('io.quarkus.runtime.configuration.ConfigDiagnostic');
  await expect(page.locator('#method-count')).toHaveText('17');
  await expect(page.locator('#constructor-count')).toHaveText('1');
  await expect(page.locator('#field-count')).toHaveText('6');
  await expect(page.locator('#source-lines')).toHaveText('268');
  await expect(page.locator('#selected-source')).toHaveText('core/runtime/src/main/java/io/quarkus/runtime/configuration/ConfigDiagnostic.java:36');
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.getByRole('button', { name: 'Copy path:line' }).click();
  await expect(page.locator('#copy-status')).toHaveText('Source location copied.');
  expect(await page.evaluate(() => navigator.clipboard.readText()))
    .toBe('core/runtime/src/main/java/io/quarkus/runtime/configuration/ConfigDiagnostic.java:36');
  await search.fill('');
  await expect(page.locator('#type-list button[aria-pressed="true"]')).toBeInViewport();
});

test('camera, orbit, and color controls change the actual rendered city', async ({ page }) => {
  await open(page);
  await page.getByRole('searchbox').fill('ConfigDiagnostic');
  await page.locator('#type-list button').click();
  const canvas = page.locator('canvas');
  const initial = await canvas.screenshot();
  await page.getByRole('button', { name: 'Focus selected' }).click();
  const focused = await canvas.screenshot();
  expect(focused.equals(initial)).toBe(false);
  await page.getByRole('button', { name: 'Reset camera' }).click();
  const reset = await canvas.screenshot();
  expect(reset.equals(focused)).toBe(false);
  await page.getByLabel('Color buildings by').selectOption('fieldCount');
  expect((await canvas.screenshot()).equals(reset)).toBe(false);
  await expect(page.locator('#legend-title')).toHaveText('Fields');
  const beforeOrbit = await canvas.screenshot();
  const bounds = (await canvas.boundingBox())!;
  await page.mouse.move(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  await page.mouse.down();
  await page.mouse.move(bounds.x + bounds.width / 2 + 90, bounds.y + bounds.height / 2 + 30, { steps: 8 });
  await page.mouse.up();
  expect((await canvas.screenshot()).equals(beforeOrbit)).toBe(false);
  await expect(page.locator('#selected-name')).toHaveText('io.quarkus.runtime.configuration.ConfigDiagnostic');
});

test('imports the reproducible local fixture and selects a building using real ray picking', async ({ page }) => {
  await open(page);
  await importDocument(page, synthetic, 'synthetic.snapshot.json');
  await expect(page.locator('#result-count')).toHaveText('9');
  await expect(page.locator('#canvas-host')).toHaveAttribute('data-state', 'ready');
  const bounds = (await page.locator('canvas').boundingBox())!;
  let picked = false;
  for (const y of [0.45, 0.55, 0.35, 0.65, 0.25, 0.75]) {
    for (const x of [0.5, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8]) {
      await page.mouse.move(bounds.x + bounds.width * x, bounds.y + bounds.height * y);
      if (await page.getByRole('tooltip').isVisible()) {
        const name = await page.locator('#tooltip-name').innerText();
        await expect(page.locator('#tooltip-metrics')).toContainText('declared methods');
        await page.mouse.click(bounds.x + bounds.width * x, bounds.y + bounds.height * y);
        await expect(page.locator('#selected-name')).toContainText(name);
        picked = true;
        break;
      }
    }
    if (picked) break;
  }
  expect(picked, 'A visible fixture building must be ray-selectable').toBe(true);
  await page.getByRole('button', { name: 'Load Quarkus sample' }).click();
  await expect(page.locator('#result-count')).toHaveText('219');
});

test('supports drag-and-drop and keyboard result navigation with duplicate qualified names', async ({ page }) => {
  await open(page);
  const transfer = await page.evaluateHandle(document => {
    const data = new DataTransfer();
    data.items.add(new File([JSON.stringify(document)], 'dropped.json', { type: 'application/json' }));
    return data;
  }, synthetic);
  await page.locator('.city-panel').dispatchEvent('drop', { dataTransfer: transfer });
  await expect(page.locator('#status')).toHaveText('Local file: dropped.json');
  const search = page.getByRole('searchbox');
  await search.fill('Shared');
  await expect(page.locator('#result-count')).toHaveText('2');
  await search.press('ArrowDown');
  await page.keyboard.press('End');
  await page.keyboard.press('Enter');
  await expect(page.locator('#selected-module')).toHaveText('app');
  await search.fill('no-such-type');
  await expect(page.locator('#result-count')).toHaveText('0');
  await expect(page.locator('#no-results')).toBeVisible();
  await expect(page.locator('#selected-module')).toHaveText('app');
});

test('explains malformed and semantically invalid imports while retaining the loaded city', async ({ page }) => {
  await open(page);
  await page.getByRole('searchbox').fill('ConfigDiagnostic');
  await page.locator('#type-list button').click();
  await page.locator('#file-input').setInputFiles({ name: 'broken.json', mimeType: 'application/json', buffer: Buffer.from('{') });
  await expect(page.getByRole('alert')).toContainText('not valid JSON');
  await expect(page.locator('#selected-name')).toContainText('ConfigDiagnostic');
  const bad = structuredClone(synthetic);
  bad.summary.typeCountHead++;
  await importDocument(page, bad);
  await expect(page.getByRole('alert')).toContainText('summary.typeCountHead');
  await page.getByRole('button', { name: 'Dismiss', exact: true }).click();
  await expect(page.getByRole('alert')).toBeHidden();
  await expect(page.locator('#repository-name')).toHaveText('quarkus');
});

test('handles empty snapshots and displays incomplete analysis prominently', async ({ page }) => {
  await open(page);
  const empty = structuredClone(synthetic);
  empty.types = [];
  empty.summary.typeCountHead = 0;
  empty.summary.unchanged = 0;
  await importDocument(page, empty);
  await expect(page.locator('#canvas-host')).toHaveAttribute('data-state', 'empty');
  await expect(page.getByRole('heading', { name: 'No types in this snapshot' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Focus selected' })).toBeDisabled();
  const incomplete = structuredClone(synthetic);
  incomplete.summary.incomplete = true;
  incomplete.diagnostics.push({ severity: 'error', code: 'PARSE_ERROR', revision: 'head',
    path: 'src/main/java/Bad.java', line: 3, message: 'Source could not be parsed.', affectedTypeIds: [] });
  await importDocument(page, incomplete);
  await expect(page.locator('#diagnostics')).toBeVisible();
  await expect(page.locator('#diagnostics')).toHaveAttribute('open', '');
  await expect(page.locator('#diagnostics-summary')).toHaveText('Incomplete snapshot · 1 analysis error');
  await expect(page.locator('#diagnostics-list')).toContainText('Bad.java:3');
  await expect(page.locator('#result-count')).toHaveText('9');
});

test('retains searchable data when WebGL 2 cannot initialize', async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, ...args: Parameters<typeof original>) {
      if (String(args[0]).includes('webgl')) return null;
      return original.apply(this, args);
    } as typeof original;
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '3D view unavailable' })).toBeVisible();
  await expect(page.locator('#result-count')).toHaveText('219');
  await page.getByRole('searchbox').fill('ConfigDiagnostic');
  await page.locator('#type-list button').click();
  await expect(page.locator('#method-count')).toHaveText('17');
  await expect(page.getByRole('button', { name: 'Focus selected' })).toBeDisabled();
});

test('reports clipboard failure without losing the source location', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: async () => { throw new Error('Clipboard denied'); } },
    });
  });
  await open(page);
  await page.getByRole('searchbox').fill('ConfigDiagnostic');
  await page.locator('#type-list button').click();
  await page.getByRole('button', { name: 'Copy path:line' }).click();
  await expect(page.locator('#copy-status')).toContainText('Select and copy');
  await expect(page.locator('#selected-source')).toContainText('ConfigDiagnostic.java:36');
});

test('imports text as data and rejects invalid UTF-8', async ({ page }) => {
  await open(page);
  const doc = structuredClone(synthetic);
  doc.repository.displayName = '<img src="https://invalid.example/image" onerror="window.pwned=true">';
  await importDocument(page, doc);
  await expect(page.locator('#repository-name')).toHaveText(doc.repository.displayName);
  await expect(page.locator('img')).toHaveCount(0);
  expect(await page.evaluate(() => Reflect.get(window, 'pwned'))).toBeUndefined();
  await page.locator('#file-input').setInputFiles({
    name: 'invalid-utf8.json', mimeType: 'application/json', buffer: Buffer.from([0xc3, 0x28]),
  });
  await expect(page.getByRole('alert')).toContainText('UTF-8');
});

test('keeps selection visible through responsive layouts without horizontal overflow', async ({ page }) => {
  await open(page);
  await page.getByRole('searchbox').fill('ConfigDiagnostic');
  await page.locator('#type-list button').click();
  await page.getByRole('searchbox').fill('');
  for (const viewport of [{ width: 1280, height: 900 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await expect.poll(() => page.locator('#type-list button[aria-pressed="true"]').evaluate(button => {
      const selected = button.getBoundingClientRect();
      const list = document.getElementById('type-list')!.getBoundingClientRect();
      return selected.top >= list.top && selected.bottom <= list.bottom;
    })).toBe(true);
    await expect(page.locator('#selected-name')).toHaveText('io.quarkus.runtime.configuration.ConfigDiagnostic');
    await expect(page.locator('#selected-source')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  }
});
