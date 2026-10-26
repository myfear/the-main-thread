import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './api';

afterEach(() => vi.unstubAllGlobals());
describe('Studio API boundary', () => {
  it('does not retry an ambiguous mutating request', async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError('Network response lost'));
    vi.stubGlobal('fetch', fetch);
    await expect(request('/publications/id/runs', 'demo-token', { fixture: 'low-risk' })).rejects.toThrow('Network response lost');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('keeps server diagnostics available to the editor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ valid: false, errors: [{ code: 'INVALID_CONFIG' }] }), { status: 422 })));
    await expect(request('/graphs/publish', 'demo-token', {})).rejects.toMatchObject({ status: 422, body: { valid: false, errors: [{ code: 'INVALID_CONFIG' }] } });
  });
  it('replaces an HTML error page with a useful message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('<html>private stack trace</html>', { status: 401 }))));
    await expect(request('/node-types', 'bad')).rejects.toEqual(expect.objectContaining({ status: 401, message: expect.stringContaining('token rejected') }));
    await expect(request('/node-types', 'bad')).rejects.toBeInstanceOf(ApiError);
  });
});
