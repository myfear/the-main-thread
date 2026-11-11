const state = {
    catalog: [],
    active: null,
    settings: {},
    sourceBlob: null,
    sourceUrl: null,
    outputUrl: null,
    outputName: 'filtered.png',
    controller: null,
    renderId: 0,
    theory: 'gaussian',
    theoryRadius: 4,
    theoryId: 0,
    ditherPresets: null,
    split: 50
};

const elements = {};

document.addEventListener('DOMContentLoaded', init);

async function init() {
    elements.filterList = document.getElementById('filter-list');
    elements.filterCount = document.getElementById('filter-count');
    elements.stage = document.getElementById('stage');
    elements.beforeImage = document.getElementById('before-image');
    elements.afterImage = document.getElementById('after-image');
    elements.afterLabel = document.getElementById('after-label');
    elements.splitRange = document.getElementById('split-range');
    elements.fileInput = document.getElementById('file-input');
    elements.chooseButton = document.getElementById('choose-button');
    elements.sampleButton = document.getElementById('sample-button');
    elements.downloadButton = document.getElementById('download-button');
    elements.status = document.getElementById('status');
    elements.activeName = document.getElementById('active-name');
    elements.activeSummary = document.getElementById('active-summary');
    elements.paramControls = document.getElementById('param-controls');
    elements.metaSize = document.getElementById('meta-size');
    elements.metaTime = document.getElementById('meta-time');
    elements.theoryPanel = document.getElementById('theory-panel');

    bindStaticEvents();

    try {
        const [catalog, sample] = await Promise.all([
            fetchJson('/api/filters'),
            fetch('/api/sample.png').then((response) => {
                if (!response.ok) {
                    throw new Error('The test frame did not load');
                }
                return response.blob();
            })
        ]);
        state.catalog = catalog;
        elements.filterCount.textContent = `${catalog.length} available`;
        renderFilterList();
        setSource(sample, 'Test frame 01');
        selectFilter(catalog.find((filter) => filter.id === 'gaussian') || catalog[0]);
        elements.chooseButton.disabled = false;
        elements.sampleButton.disabled = false;
    } catch (error) {
        setStatus(error.message, { error: true });
    }
}

function bindStaticEvents() {
    elements.chooseButton.addEventListener('click', () => elements.fileInput.click());
    elements.fileInput.addEventListener('change', async () => {
        const file = elements.fileInput.files && elements.fileInput.files[0];
        elements.fileInput.value = '';
        if (!file) {
            return;
        }
        if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type)) {
            setStatus('Choose a PNG, JPEG, or GIF image.', { error: true });
            return;
        }
        if (file.size === 0 || file.size > 10 * 1024 * 1024) {
            setStatus('Choose a non-empty image no larger than 10 MiB.', { error: true });
            return;
        }
        setSource(file, file.name);
        await render();
    });

    elements.sampleButton.addEventListener('click', async () => {
        try {
            invalidateOutput();
            elements.sampleButton.disabled = true;
            setStatus('Loading test frame…', { busy: true });
            const response = await fetch('/api/sample.png');
            if (!response.ok) {
                throw new Error('The test frame did not load');
            }
            setSource(await response.blob(), 'Test frame 01');
            await render();
        } catch (error) {
            setStatus(error.message, { error: true });
        } finally {
            elements.sampleButton.disabled = false;
        }
    });

    elements.splitRange.addEventListener('input', () => {
        setSplit(Number(elements.splitRange.value));
    });

    document.querySelectorAll('[data-theory]').forEach((button) => {
        button.addEventListener('click', () => {
            state.theory = button.dataset.theory;
            document.querySelectorAll('[data-theory]').forEach((other) => {
                const active = other === button;
                other.classList.toggle('is-active', active);
                other.setAttribute('aria-selected', String(active));
                other.tabIndex = active ? 0 : -1;
            });
            renderTheory();
        });
        button.addEventListener('keydown', (event) => {
            const tabs = [...document.querySelectorAll('[data-theory]')];
            let index = tabs.indexOf(button);
            if (event.key === 'ArrowRight') index = (index + 1) % tabs.length;
            else if (event.key === 'ArrowLeft') index = (index + tabs.length - 1) % tabs.length;
            else if (event.key === 'Home') index = 0;
            else if (event.key === 'End') index = tabs.length - 1;
            else return;
            event.preventDefault();
            tabs[index].focus();
            tabs[index].click();
        });
    });
}

function setSource(blob, name) {
    invalidateOutput();
    if (state.sourceUrl) {
        URL.revokeObjectURL(state.sourceUrl);
    }
    state.sourceBlob = blob;
    state.sourceUrl = URL.createObjectURL(blob);
    elements.beforeImage.src = state.sourceUrl;
    elements.afterImage.src = state.sourceUrl;
    elements.afterLabel.textContent = 'Filtered';
    elements.metaSize.textContent = `${name || 'Uploaded image'} · reading…`;
    elements.splitRange.value = '50';
    setSplit(50);
    const probe = new Image();
    const sourceUrl = state.sourceUrl;
    probe.onload = () => {
        if (sourceUrl !== state.sourceUrl || state.outputUrl) return;
        elements.metaSize.textContent = `${name || 'Uploaded image'} · ${probe.naturalWidth} × ${probe.naturalHeight}`;
    };
    probe.onerror = () => {
        if (sourceUrl === state.sourceUrl) {
            elements.metaSize.textContent = `${name || 'Uploaded image'} · preview unavailable`;
        }
    };
    probe.src = state.sourceUrl;
}

function renderFilterList() {
    const groups = new Map();
    for (const filter of state.catalog) {
        if (!groups.has(filter.group)) {
            groups.set(filter.group, []);
        }
        groups.get(filter.group).push(filter);
    }

    elements.filterList.innerHTML = '';
    for (const [group, filters] of groups) {
        const section = document.createElement('div');
        section.className = 'filter-group';
        const heading = document.createElement('h3');
        heading.textContent = group;
        section.appendChild(heading);

        for (const filter of filters) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'filter-button';
            button.dataset.id = filter.id;
            button.setAttribute('aria-pressed', 'false');

            const name = document.createElement('span');
            name.className = 'filter-name';
            name.textContent = filter.name;
            if (filter.deepDive) {
                const dot = document.createElement('span');
                dot.className = 'filter-dot';
                dot.title = 'Has a deep-dive explanation';
                dot.setAttribute('aria-label', 'Has a deep-dive explanation');
                name.appendChild(dot);
            }

            const summary = document.createElement('span');
            summary.className = 'filter-summary';
            summary.textContent = filter.summary;

            button.append(name, summary);
            button.addEventListener('click', () => selectFilter(filter));
            section.appendChild(button);
        }
        elements.filterList.appendChild(section);
    }
}

function selectFilter(spec, overrides = {}) {
    if (!spec) {
        return;
    }
    state.active = spec;
    state.settings = {};
    for (const param of spec.params) {
        state.settings[param.name] = param.defaultValue;
    }
    Object.assign(state.settings, overrides);
    if (spec.id === 'gaussian') {
        state.theoryRadius = Number(state.settings.radius);
    }

    elements.filterList.querySelectorAll('.filter-button').forEach((button) => {
        const active = button.dataset.id === spec.id;
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-pressed', String(active));
    });

    elements.activeName.textContent = spec.name;
    elements.activeSummary.textContent = spec.summary;
    renderControls(spec);

    if (spec.deepDive) {
        state.theory = spec.id;
        document.querySelectorAll('[data-theory]').forEach((button) => {
            const active = button.dataset.theory === spec.id;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-selected', String(active));
            button.tabIndex = active ? 0 : -1;
        });
        renderTheory();
    }

    scheduleRender();
}

function renderControls(spec) {
    elements.paramControls.innerHTML = '';
    if (spec.params.length === 0) {
        const note = document.createElement('p');
        note.className = 'help';
        note.textContent = 'This filter has no settings. Select it to see the fixed transform.';
        elements.paramControls.appendChild(note);
        return;
    }

    for (const param of spec.params) {
        const control = document.createElement('div');
        control.className = 'control';

        if (param.type === 'range') {
            const top = document.createElement('div');
            top.className = 'control-top';
            const label = document.createElement('label');
            label.htmlFor = `param-${param.name}`;
            label.textContent = param.label;
            const output = document.createElement('output');
            output.htmlFor = `param-${param.name}`;
            output.textContent = formatParamValue(state.settings[param.name], param);
            top.append(label, output);

            const input = document.createElement('input');
            input.type = 'range';
            input.className = 'range';
            input.id = `param-${param.name}`;
            input.dataset.param = param.name;
            input.min = String(param.min);
            input.max = String(param.max);
            input.step = String(param.step);
            input.value = String(state.settings[param.name]);
            input.style.setProperty('--fill', fillPercent(Number(input.value), param) + '%');
            input.addEventListener('input', () => {
                const value = Number(input.value);
                state.settings[param.name] = value;
                output.textContent = formatParamValue(value, param);
                input.style.setProperty('--fill', fillPercent(value, param) + '%');
                scheduleRender();
                if (spec.id === 'gaussian' && param.name === 'radius') {
                    state.theoryRadius = value;
                    scheduleKernel();
                }
            });

            control.append(top, input);
        } else if (param.type === 'select') {
            const label = document.createElement('label');
            label.className = 'control-label';
            label.htmlFor = `param-${param.name}`;
            label.textContent = param.label;

            const select = document.createElement('select');
            select.id = `param-${param.name}`;
            select.dataset.param = param.name;
            for (const option of param.options) {
                const item = document.createElement('option');
                item.value = option.value;
                item.textContent = option.label;
                select.appendChild(item);
            }
            select.value = state.settings[param.name];
            select.addEventListener('change', () => {
                state.settings[param.name] = select.value;
                updateMatrixHighlight();
                scheduleRender();
            });

            control.append(label, select);
        } else if (param.type === 'toggle') {
            const row = document.createElement('label');
            row.className = 'toggle-row';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.id = `param-${param.name}`;
            input.dataset.param = param.name;
            input.checked = Boolean(state.settings[param.name]);
            input.addEventListener('change', () => {
                state.settings[param.name] = input.checked;
                scheduleRender();
            });
            const text = document.createElement('span');
            text.textContent = param.label;
            row.append(input, text);
            control.appendChild(row);
        }

        if (param.help) {
            const help = document.createElement('p');
            help.className = 'help';
            help.textContent = param.help;
            control.appendChild(help);
        }
        elements.paramControls.appendChild(control);
    }
}

function formatParamValue(value, param) {
    const number = Number(value);
    const decimals = String(param.step || 1).split('.')[1]?.length || 0;
    const text = number.toFixed(decimals);
    return param.unit ? `${text} ${param.unit}` : text;
}

function fillPercent(value, param) {
    const range = Number(param.max) - Number(param.min);
    return range === 0 ? 0 : ((value - Number(param.min)) / range) * 100;
}

function setSplit(value) {
    state.split = Math.max(0, Math.min(100, value));
    elements.stage.style.setProperty('--split', `${state.split}%`);
    elements.splitRange.value = String(Math.round(state.split));
}

let renderTimer = 0;
function invalidateOutput() {
    window.clearTimeout(renderTimer);
    state.renderId++;
    state.controller?.abort();
    elements.downloadButton.removeAttribute('href');
    elements.downloadButton.setAttribute('aria-disabled', 'true');
    elements.downloadButton.classList.add('is-disabled');
    elements.metaTime.textContent = '';
    if (state.sourceUrl) elements.afterImage.src = state.sourceUrl;
    elements.afterLabel.textContent = 'Waiting for render';
    if (state.outputUrl) {
        URL.revokeObjectURL(state.outputUrl);
        state.outputUrl = null;
    }
}

function scheduleRender() {
    invalidateOutput();
    setStatus('Waiting to render…', { busy: true });
    renderTimer = window.setTimeout(render, 140);
}

let kernelTimer = 0;
function scheduleKernel() {
    window.clearTimeout(kernelTimer);
    const radius = state.theoryRadius;
    state.theoryId++;
    kernelTimer = window.setTimeout(() => loadGaussianTheory(radius), 140);
}

async function render() {
    if (!state.sourceBlob || !state.active) {
        return;
    }
    window.clearTimeout(renderTimer);
    invalidateOutput();
    const id = state.renderId;
    const active = state.active;
    if (state.controller) {
        state.controller.abort();
    }
    const controller = new AbortController();
    state.controller = controller;
    setStatus('Rendering…', { busy: true });

    const form = new FormData();
    form.append('image', state.sourceBlob, 'source.png');
    form.append('filter', state.active.id);
    form.append('settings', JSON.stringify(state.settings));

    try {
        const response = await fetch('/api/render', {
            method: 'POST',
            body: form,
            signal: controller.signal
        });
        if (!response.ok) {
            let message = response.status === 413
                ? 'The upload is too large. Choose an image under 10 MiB.'
                : `Render failed (${response.status}). Try another image.`;
            try {
                const body = await response.json();
                if (body && body.error) {
                    message = body.error;
                }
            } catch (ignored) {
                // Keep the status line.
            }
            throw new Error(message);
        }
        const blob = await response.blob();
        if (id !== state.renderId) {
            return;
        }
        const width = response.headers.get('X-Image-Width');
        const height = response.headers.get('X-Image-Height');
        const bytes = response.headers.get('X-Output-Bytes');
        const millis = response.headers.get('X-Render-Millis');

        const nextUrl = URL.createObjectURL(blob);
        const probe = new Image();
        probe.onload = () => {
            if (id !== state.renderId) {
                URL.revokeObjectURL(nextUrl);
                return;
            }
            if (state.outputUrl) {
                URL.revokeObjectURL(state.outputUrl);
            }
            state.outputUrl = nextUrl;
            elements.afterImage.src = nextUrl;
            elements.afterLabel.textContent = active.name;
            state.outputName = `filter-${active.id}.png`;
            elements.downloadButton.href = nextUrl;
            elements.downloadButton.download = state.outputName;
            elements.downloadButton.classList.remove('is-disabled');
            elements.downloadButton.setAttribute('aria-disabled', 'false');
            elements.metaSize.textContent = `${width} × ${height}${bytes ? ` · ${formatBytes(Number(bytes))}` : ''}`;
            elements.metaTime.textContent = millis ? `server render ${millis} ms` : '';
            setStatus('Rendered');
        };
        probe.onerror = () => {
            URL.revokeObjectURL(nextUrl);
            if (id !== state.renderId) return;
            setStatus('The filtered image could not be decoded', { error: true });
        };
        probe.src = nextUrl;
    } catch (error) {
        if (error.name === 'AbortError') {
            return;
        }
        if (id === state.renderId) {
            setStatus(error.message, { error: true });
        }
    }
}

function setStatus(message, options = {}) {
    elements.status.textContent = message;
    elements.status.classList.toggle('is-busy', Boolean(options.busy));
    elements.status.classList.toggle('is-error', Boolean(options.error));
    if (options.error && !state.outputUrl) elements.afterLabel.textContent = 'No result';
}

function renderTheory() {
    window.clearTimeout(kernelTimer);
    state.theoryId++;
    elements.theoryPanel.setAttribute('aria-labelledby', `theory-${state.theory}`);
    if (state.theory === 'gaussian') {
        elements.theoryPanel.innerHTML = `
            <div class="theory-layout">
                <div class="theory-copy">
                    <h3>Blur is a weighted average.</h3>
                    <p>Each pixel in the output takes values from a line of neighbours and multiplies them by the kernel weights. The weights come from a normal distribution. The pixel in the middle counts most; the ones near the edge count very little.</p>
                    <p>The library does not build a two-dimensional grid of weights. <code>GaussianFilter.makeKernel</code> returns a one-dimensional kernel, and <code>convolveAndTranspose</code> runs it across the rows, transposes the result, and runs it again. Two passes with 13 taps cost less than one pass with 169.</p>
                    <p><strong>Radius is not sigma.</strong> The code sets <code>sigma = radius / 3</code> and uses <code>2 * ceil(radius) + 1</code> taps. A radius of 6 gives sigma 2 and 13 taps. Pixels at the border are clamped to the nearest real pixel, which keeps the edge from fading to black.</p>
                    <div class="callout">A 13 × 13 kernel has 169 sample positions per pixel. Two separable 13-tap passes have 26.</div>
                </div>
                <div class="theory-visual">
                    <div class="visual-head"><span>1D kernel</span><span id="kernel-meta">…</span></div>
                    <div id="kernel-bars" class="kernel-bars" aria-label="Gaussian kernel weights"></div>
                    <p id="kernel-caption" class="caption">Loading the kernel weights…</p>
                </div>
            </div>`;
        loadGaussianTheory(Number(state.theoryRadius));
        return;
    }

    elements.theoryPanel.innerHTML = `
        <div class="theory-layout">
            <div class="theory-copy">
                <h3>Quantising with a plan.</h3>
                <p>Reducing a channel from 256 values to 3 means every pixel has to jump to 0, 127 or 255. Round each value on its own and smooth gradients turn into visible bands.</p>
                <p>Ordered dithering uses a small matrix of thresholds. The matrix tiles over the image. At each pixel the filter looks up a threshold, compares it with a scaled version of the channel value, and chooses the level just below or just above. The choice changes from pixel to pixel, so the eye averages the pattern back into a tone.</p>
                <p><code>DitherFilter</code> builds two lookup tables, <code>div</code> and <code>mod</code>, and uses them for speed. With <code>levels = 3</code> the output channels can only be 0, 127 or 255. <code>colorDither</code> applies the decision to red, green and blue separately. Turn it off and the filter uses the average of the three channels, which gives a monochrome pattern.</p>
                <p>In M2, <code>mod</code> scales the full byte value rather than the fractional remainder between output levels. At more than two levels, the pattern can shift the average tone.</p>
                <p>Halftone matrices arrange the thresholds in clusters, closer to the dots a printing plate carries. A 6 x 6 halftone tile makes a coarser structure than the 4 x 4 magic matrix.</p>
                <div class="callout">Pick Quantize to compare an image-specific palette with fixed channel levels. Its error-diffusion option is disabled here: the M2 implementation does not propagate errors to adjacent rows correctly.</div>
            </div>
            <div class="theory-visual">
                <div class="visual-head"><span>Threshold matrices</span><span>click to apply</span></div>
                <div id="dither-grids" class="matrix-grids"></div>
                <p class="caption">Each tile repeats across the image. Lower numbers round up sooner; higher numbers wait longer.</p>
            </div>
        </div>`;
    loadDitherTheory();
}

async function loadGaussianTheory(radius) {
    const bars = document.getElementById('kernel-bars');
    if (!bars) {
        return;
    }
    const id = ++state.theoryId;
    try {
        const theory = await fetchJson(`/api/theory/gaussian?radius=${encodeURIComponent(radius)}`);
        if (id !== state.theoryId || state.theory !== 'gaussian'
                || document.getElementById('kernel-bars') !== bars) {
            return;
        }
        const max = Math.max(...theory.weights);
        bars.setAttribute('role', 'img');
        bars.setAttribute('aria-label', `Kernel weights: ${theory.weights.join(', ')}`);
        bars.innerHTML = '';
        for (const weight of theory.weights) {
            const bar = document.createElement('div');
            bar.className = 'kernel-bar';
            bar.style.height = `${Math.max(2, (weight / max) * 100)}%`;
            bar.title = weight.toFixed(6);
            bars.appendChild(bar);
        }
        const meta = document.getElementById('kernel-meta');
        if (meta) {
            meta.textContent = `${theory.taps} taps · σ ${Number(theory.sigma).toFixed(2)}`;
        }
        const caption = document.getElementById('kernel-caption');
        if (caption) {
            caption.textContent = theory.taps === 1
                ? 'Radius 0 produces a one-tap kernel with weight 1. The filter returns a copy.'
                : `Normalised weights preserve a constant colour, apart from rounding. Radius ${theory.radius} becomes sigma ${Number(theory.sigma).toFixed(2)}.`;
        }
    } catch (error) {
        if (id !== state.theoryId) return;
        const caption = document.getElementById('kernel-caption');
        if (caption) {
            caption.textContent = error.message;
        }
    }
}

async function loadDitherTheory() {
    const host = document.getElementById('dither-grids');
    if (!host) {
        return;
    }
    try {
        if (!state.ditherPresets) {
            state.ditherPresets = await fetchJson('/api/theory/dither');
        }
        if (state.theory !== 'dither' || document.getElementById('dither-grids') !== host) {
            return;
        }
        host.innerHTML = '';
        for (const preset of state.ditherPresets) {
            const card = document.createElement('button');
            card.type = 'button';
            card.className = 'matrix-card';
            card.dataset.matrix = preset.key;
            card.setAttribute('aria-label', `${preset.label}. ${preset.note}`);

            const head = document.createElement('div');
            head.className = 'matrix-card-head';
            const name = document.createElement('span');
            name.textContent = preset.label;
            const size = document.createElement('span');
            size.textContent = `${preset.size}×${preset.size}`;
            head.append(name, size);

            const grid = document.createElement('div');
            grid.className = 'matrix-grid';
            grid.style.gridTemplateColumns = `repeat(${preset.size}, 1fr)`;
            const max = Math.max(...preset.matrix);
            for (const value of preset.matrix) {
                const cell = document.createElement('span');
                cell.className = 'matrix-cell';
                const t = max === 0 ? 0 : value / max;
                cell.style.backgroundColor = `rgba(231, 144, 111, ${0.07 + t * 0.82})`;
                cell.style.color = t > 0.58 ? '#17231f' : 'rgba(239, 232, 218, 0.72)';
                if (preset.size <= 8) {
                    cell.textContent = String(value);
                } else {
                    cell.classList.add('is-dense');
                    cell.title = String(value);
                }
                grid.appendChild(cell);
            }

            card.append(head, grid);
            card.addEventListener('click', () => applyMatrix(preset.key));
            host.appendChild(card);
        }
        updateMatrixHighlight();
    } catch (error) {
        host.innerHTML = `<p class="caption">${escapeHtml(error.message)}</p>`;
    }
}

function applyMatrix(key) {
    if (!state.active || state.active.id !== 'dither') {
        const dither = state.catalog.find((filter) => filter.id === 'dither');
        selectFilter(dither, { matrix: key });
        return;
    }
    state.settings.matrix = key;
    const select = elements.paramControls.querySelector('[data-param="matrix"]');
    if (select) {
        select.value = key;
    }
    updateMatrixHighlight();
    scheduleRender();
}

function updateMatrixHighlight() {
    const activeKey = state.active && state.active.id === 'dither' ? state.settings.matrix : null;
    document.querySelectorAll('.matrix-card').forEach((card) => {
        card.classList.toggle('is-active', card.dataset.matrix === activeKey);
        card.setAttribute('aria-pressed', String(card.dataset.matrix === activeKey));
    });
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Request failed (${response.status})`);
    }
    return response.json();
}

function formatBytes(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(0)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}
