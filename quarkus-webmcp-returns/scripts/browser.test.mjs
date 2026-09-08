import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';

const base = process.env.DEMO_URL ?? 'http://127.0.0.1:8097';
const valid = { items: [{ sku: 'KEYBOARD', quantity: 1 }, { sku: 'CABLE', quantity: 1 }], reason: 'damaged' };
let browser;

before(async () => {
  browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--enable-features=WebMCP'] });
});
after(async () => { await browser?.close(); });

async function open(t) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  t.after(() => page.close());
  await page.goto(base);
  await page.waitForFunction(() => document.querySelector('#tool-status').textContent === '3 WebMCP tools ready');
  return page;
}

async function call(page, name, input = {}) {
  return page.evaluate(async ({ name, input }) => {
    const tool = (await document.modelContext.getTools()).find(tool => tool.name === name);
    return JSON.parse(await document.modelContext.executeTool(tool, JSON.stringify(input)));
  }, { name, input });
}

test('native WebMCP registers exactly the intended tools', async t => {
  const page = await open(t);
  assert.deepEqual(await page.evaluate(async () => (await document.modelContext.getTools()).map(tool => tool.name)),
    ['clear_return_draft', 'get_current_order', 'prepare_return']);
  const state = await call(page, 'get_current_order');
  assert.equal(state.order.id, 'ORD-1042');
  assert.equal(state.draft, null);
});

test('a tool result, visible draft, and form agree; repetition replaces the draft', async t => {
  const page = await open(t);
  const first = await call(page, 'prepare_return', valid);
  assert.equal(first.draft.refundCents, 10800);
  assert.equal(first.draft.submitted, false);
  assert.equal(await page.locator('#refund-total').innerText(), '€108.00');
  assert.equal(await page.locator('#quantity-CABLE').inputValue(), '1');
  assert.deepEqual(await call(page, 'prepare_return', valid), first);
  assert.equal(await page.locator('.draft-line').count(), 2);
  await mkdir('evidence', { recursive: true });
  await page.screenshot({ path: 'evidence/return-draft.png', fullPage: true });
});

test('rejected backend validation preserves the prior draft', async t => {
  const page = await open(t);
  await call(page, 'prepare_return', valid);
  for (const items of [[{ sku: 'CABLE', quantity: 3 }], [{ sku: 'GIFT-CARD', quantity: 1 }]]) {
    await assert.rejects(call(page, 'prepare_return', { items, reason: 'damaged' }));
    assert.equal((await call(page, 'get_current_order')).draft.refundCents, 10800);
    assert.equal(await page.locator('#refund-total').innerText(), '€108.00');
  }
});

test('a cleared draft cannot be restored by a late response', async t => {
  const page = await open(t);
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let arrived;
  const received = new Promise(resolve => { arrived = resolve; });
  await page.route('**/return-preview', async route => { arrived(); await gate; await route.continue(); });
  const pending = call(page, 'prepare_return', valid).then(() => 'success', () => 'rejected');
  await received;
  await call(page, 'clear_return_draft');
  release();
  assert.equal(await pending, 'rejected');
  assert.equal((await call(page, 'get_current_order')).draft, null);
  assert.match(await page.locator('#draft').innerText(), /No draft yet/);
});

test('cancelled tool execution does not commit a draft', async t => {
  const page = await open(t);
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let arrived;
  const received = new Promise(resolve => { arrived = resolve; });
  await page.route('**/return-preview', async route => {
    arrived(); await gate;
    await route.abort().catch(() => {});
  });
  const pending = page.evaluate(async input => {
    const tool = (await document.modelContext.getTools()).find(tool => tool.name === 'prepare_return');
    window.testController = new AbortController();
    try {
      await document.modelContext.executeTool(tool, JSON.stringify(input), { signal: window.testController.signal });
      return 'success';
    } catch { return 'cancelled'; }
  }, valid);
  await received;
  await page.evaluate(() => window.testController.abort());
  release();
  assert.equal(await pending, 'cancelled');
  assert.equal((await call(page, 'get_current_order')).draft, null);
});

test('reload discards the page draft and restores exactly three tools', async t => {
  const page = await open(t);
  await call(page, 'prepare_return', valid);
  await page.reload();
  await page.waitForFunction(() => document.querySelector('#tool-status').textContent === '3 WebMCP tools ready');
  assert.equal((await call(page, 'get_current_order')).draft, null);
  assert.equal(await page.evaluate(async () => (await document.modelContext.getTools()).length), 3);
});

test('the human form works when the native API is unavailable', async t => {
  const plainBrowser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--disable-features=WebMCP'] });
  t.after(() => plainBrowser.close());
  const page = await plainBrowser.newPage();
  await page.goto(base);
  await page.waitForFunction(() => document.querySelector('#tool-status').textContent.includes('form is ready'));
  assert.equal(await page.evaluate(() => !!document.modelContext), false);
  await page.locator('#quantity-CABLE').fill('2');
  await page.getByRole('button', { name: 'Prepare return' }).click();
  await page.locator('#refund-total').waitFor();
  assert.equal(await page.locator('#refund-total').innerText(), '€38.00');
  await page.getByRole('button', { name: 'Clear draft' }).click();
  assert.match(await page.locator('#draft').innerText(), /No draft yet/);
});

test('the workbench fits a narrow viewport', async t => {
  const page = await open(t);
  await page.setViewportSize({ width: 390, height: 844 });
  await call(page, 'prepare_return', valid);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.screenshot({ path: 'evidence/return-draft-mobile.png', fullPage: true });
});
