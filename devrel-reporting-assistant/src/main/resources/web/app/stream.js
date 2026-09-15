/** Small AG-UI SSE client. POST starts a run; GET recovery never repeats a prompt. */
export async function api(path = '', body, method = 'GET') {
    const response = await fetch(`/api/report${path}`, {
        method,
        headers: {'Content-Type': 'application/json'},
        ...(body === undefined ? {} : {body: JSON.stringify(body)})
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        const failure = new Error(error.message || `Request failed (${response.status})`);
        failure.status = response.status;
        throw failure;
    }
    return response.json();
}

export async function readEvents(path, options, onEvent) {
    const response = await fetch(path, {...options, headers: {'Content-Type': 'application/json', Accept: 'text/event-stream'}});
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || `Stream failed (${response.status})`);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let event = {data: [], id: null};
    const line = value => {
        value = value.replace(/\r$/, '');
        if (value === '') {
            if (event.data.length) onEvent(JSON.parse(event.data.join('\n')), event.id);
            event = {data: [], id: null};
        } else if (value.startsWith('data:')) event.data.push(value.slice(5).replace(/^ /, ''));
        else if (value.startsWith('id:')) event.id = Number(value.slice(3).trim());
    };
    try {
        while (true) {
            const {value, done} = await reader.read();
            buffer += done ? decoder.decode() : decoder.decode(value, {stream: true});
            let end;
            while ((end = buffer.indexOf('\n')) >= 0) {
                line(buffer.slice(0, end));
                buffer = buffer.slice(end + 1);
            }
            if (done) break;
        }
    } finally { reader.releaseLock(); }
}
