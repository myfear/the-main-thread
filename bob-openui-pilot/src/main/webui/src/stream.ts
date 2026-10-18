/** OpenUI 0.18.1 requires `data: `; Quarkus emits the equally valid `data:`. */
export function normalizeSse(response: Response): Response {
  if (!response.body) throw new Error('The server returned no stream.');
  let pending = '';
  const encoder = new TextEncoder();
  const body = response.body.pipeThrough(new TextDecoderStream()).pipeThrough(new TransformStream<string, Uint8Array>({
    transform(chunk, controller) {
      pending += chunk;
      let boundary: number;
      while ((boundary = pending.indexOf('\n')) >= 0) {
        const line = pending.slice(0, boundary);
        pending = pending.slice(boundary + 1);
        controller.enqueue(encoder.encode(line.replace(/^data:(?! )/, 'data: ') + '\n'));
      }
    },
    flush(controller) {
      if (pending) controller.enqueue(encoder.encode(pending.replace(/^data:(?! )/, 'data: ')));
    },
  }));
  return new Response(body, { headers: response.headers, status: response.status });
}
