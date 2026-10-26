export class ApiError extends Error {
  constructor(public status: number, public body: Record<string, unknown>, message: string) { super(message); }
}
export async function request<T>(path: string, token: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(12_000)]) : AbortSignal.timeout(12_000),
  });
  const raw = await response.text();
  let data: Record<string, unknown> = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch { /* Never render a server HTML error as UI markup. */ }
  if (!response.ok) {
    const fallback = response.status === 401 ? 'Studio token rejected. Update the connection token and reconnect.'
      : response.status === 409 ? 'This version already exists. Choose a new version and publish again.'
      : response.status === 422 ? 'The backend rejected this graph. Review the validation diagnostics.'
      : `Request failed (HTTP ${response.status}). Check the backend and retry the read operation.`;
    throw new ApiError(response.status, data, typeof data.message === 'string' ? data.message : fallback);
  }
  return data as T;
}
