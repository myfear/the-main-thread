import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const base = process.env.DEMO_URL ?? 'http://127.0.0.1:8097';
const bridge = new Client({ name: 'webmcp-contract-test', version: '1.0.0' });
const backend = new Client({ name: 'return-contract-test', version: '1.0.0' });
const evidence = [];
const input = { items: [{ sku: 'KEYBOARD', quantity: 1 }, { sku: 'CABLE', quantity: 1 }], reason: 'damaged' };
const text = result => result.content?.filter(item => item.type === 'text').map(item => item.text).join('\n') ?? '';
async function call(client, name, args) {
  console.log(`Calling ${name}`);
  const result = await client.callTool({ name, arguments: args });
  evidence.push({ name, arguments: args, result });
  assert.ok(!result.isError, text(result));
  return result;
}

try {
  await bridge.connect(new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('../node_modules/chrome-devtools-mcp/build/src/bin/chrome-devtools-mcp.js', import.meta.url)),
      '--headless', '--isolated', '--categoryExperimentalWebmcp', '--chromeArg=--enable-features=WebMCP',
      '--no-usage-statistics', '--no-performance-crux'], stderr: 'inherit'
  }));
  const pageResult = await call(bridge, 'new_page', { url: base });
  const match = text(pageResult).match(/(?:^|\n)(\d+):[^\n]*\[selected\]/)
    ?? text(pageResult).match(/(?:^|\n)(\d+):/);
  assert.ok(match, `Could not identify new page: ${text(pageResult)}`);
  const pageId = Number(match[1]);
  await call(bridge, 'wait_for', { pageId, text: ['3 WebMCP tools ready'] });
  const tools = await call(bridge, 'list_webmcp_tools', { pageId });
  assert.match(text(tools), /prepare_return/);
  await call(bridge, 'execute_webmcp_tool', { pageId, toolName: 'get_current_order', input: '{}' });
  const prepared = await call(bridge, 'execute_webmcp_tool', { pageId, toolName: 'prepare_return', input: JSON.stringify(input) });
  assert.match(text(prepared), /10800/);
  const before = text(await call(bridge, 'take_snapshot', { pageId }));
  assert.match(before, /108\.00/);

  await backend.connect(new StreamableHTTPClientTransport(new URL('/mcp', base)));
  const backendTools = await backend.listTools();
  assert.deepEqual(backendTools.tools.map(tool => tool.name), ['check_return_eligibility']);
  const checked = await call(backend, 'check_return_eligibility', { orderId: 'ORD-1042', ...input });
  assert.match(text(checked), /10800/);
  const invalid = await backend.callTool({ name: 'check_return_eligibility', arguments: {
    orderId: 'ORD-1042', items: [{ sku: 'GIFT-CARD', quantity: 1 }], reason: 'damaged'
  } });
  assert.equal(invalid.isError, true);
  evidence.push({ name: 'backend-invalid-item', result: invalid });
  await call(bridge, 'execute_webmcp_tool', { pageId, toolName: 'clear_return_draft', input: '{}' });
  await call(backend, 'check_return_eligibility', { orderId: 'ORD-1042', ...input });
  const state = await call(bridge, 'execute_webmcp_tool', { pageId, toolName: 'get_current_order', input: '{}' });
  assert.match(text(state), /"draft"\s*:\s*null/);
  console.log('PASS: native WebMCP discovery/execution, visible €108.00 draft, backend MCP eligibility/error, and page/backend separation.');
  await mkdir('evidence', { recursive: true });
  await writeFile('evidence/mcp-smoke.json', JSON.stringify({ testedAt: new Date().toISOString(), evidence }, null, 2) + '\n');
} finally {
  await backend.close();
  await bridge.close();
}
