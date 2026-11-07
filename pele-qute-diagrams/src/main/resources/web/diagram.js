import { mount, register, render } from 'pele/core';
import flowchart from 'pele/diagrams/flowchart';
import { download, rasterize, snapshotSvg } from './export.js';

register(flowchart);

export function startLab() {
    const $ = id => document.getElementById(id);
    const source = $('source');
    const initialSource = source.value;
    const live = $('live-diagram');
    const scaled = $('scaled-diagram');
    const width = $('width');
    const layout = $('layout');
    const theme = $('theme');
    const capture = $('capture');
    const error = $('render-error');
    const exportStatus = $('export-status');
    const options = { links: false, images: false, limit: 10_000, maxEdges: 200, outputLimit: 1_000_000 };
    let valid = false;
    let busy = false;
    let revision = 0;
    let snapshot;
    let diagram;

    function controls() {
        capture.disabled = !valid || busy;
        $('download-svg').disabled = !valid || !snapshot || busy;
        $('download-png').disabled = !valid || !snapshot || busy;
    }

    function showError(cause) {
        valid = false;
        document.querySelector('.lab').classList.add('has-error');
        error.textContent = `${cause.message || cause} Correct the source or restore the sample.`;
        error.hidden = false;
        controls();
    }

    function dimensions() {
        const element = (layout.value === 'adapt' ? live : scaled).querySelector('svg');
        const actualWidth = Math.round($('diagram-width').getBoundingClientRect().width);
        const box = element?.viewBox.baseVal;
        $('dimensions').textContent = box
            ? `Container ${actualWidth} px · Drawing ${Math.ceil(box.width)} × ${Math.ceil(box.height)}`
            : `Container ${actualWidth} px`;
    }

    function clearSnapshot() {
        revision++;
        if (snapshot) {
            URL.revokeObjectURL(snapshot.svgUrl);
            URL.revokeObjectURL(snapshot.pngUrl);
        }
        snapshot = undefined;
        $('previews').hidden = true;
        $('svg-preview').removeAttribute('src');
        $('png-preview').removeAttribute('src');
        exportStatus.textContent = 'No snapshot yet. Choose a layout, then create one.';
        controls();
    }

    function updateSource() {
        clearSnapshot();
        if (!source.value.trim()) {
            live.replaceChildren();
            scaled.replaceChildren();
            diagram?.destroy();
            diagram = undefined;
            showError(new Error('Enter a Mermaid flowchart.'));
            return;
        }
        try {
            const baseline = render(source.value, { ...options, autoDirection: false, idPrefix: 'scaled' });
            scaled.innerHTML = baseline.svg;
            const mountOptions = {
                ...options,
                zoom: 'auto',
                onRender: () => { dimensions(); },
                onError: showError
            };
            if (diagram) diagram.update(source.value, mountOptions);
            else diagram = mount(live, source.value, mountOptions);
            if (diagram.error) throw diagram.error;
            valid = true;
            error.hidden = true;
            document.querySelector('.lab').classList.remove('has-error');
        } catch (cause) {
            showError(cause);
        }
        dimensions();
        controls();
    }

    function updateWidth() {
        $('requested-width').value = `${width.value} px`;
        $('diagram-width').style.setProperty('--diagram-width', `${width.value}px`);
    }

    width.addEventListener('input', updateWidth);
    document.querySelectorAll('[data-width]').forEach(button => {
        button.addEventListener('click', () => { width.value = button.dataset.width; updateWidth(); });
    });
    $('details-toggle').addEventListener('click', () => {
        const open = $('order-details').hidden;
        $('order-details').hidden = !open;
        $('workspace').classList.toggle('with-details', open);
        $('details-toggle').setAttribute('aria-expanded', String(open));
        $('details-toggle').textContent = open ? 'Hide order details' : 'Show order details';
    });
    layout.addEventListener('change', () => {
        live.hidden = layout.value !== 'adapt';
        scaled.hidden = layout.value !== 'scale';
        $('layout-help').textContent = layout.value === 'adapt'
            ? 'The live layout follows its container.' : 'The original layout scales down. Its node positions stay fixed.';
        diagram?.refresh();
        dimensions();
    });
    theme.addEventListener('change', () => { $('canvas-area').dataset.theme = theme.value; });
    source.addEventListener('input', updateSource);
    $('reset-source').addEventListener('click', () => { source.value = initialSource; updateSource(); });

    capture.addEventListener('click', async () => {
        if (!valid || busy) return;
        const token = revision;
        busy = true;
        controls();
        exportStatus.textContent = 'Creating SVG and PNG snapshots…';
        try {
            const element = (layout.value === 'adapt' ? live : scaled).querySelector('svg');
            const svg = snapshotSvg(element);
            const capturedWidth = Math.round($('diagram-width').getBoundingClientRect().width);
            const capturedTheme = theme.options[theme.selectedIndex].text;
            const png = await rasterize(svg, Number($('density').value));
            // Editing during image decoding must not resurrect the previous source.
            if (revision !== token) return;
            clearSnapshot();
            snapshot = { svgUrl: URL.createObjectURL(svg.blob), pngUrl: URL.createObjectURL(png.blob) };
            $('svg-preview').src = snapshot.svgUrl;
            $('png-preview').src = snapshot.pngUrl;
            $('png-size').textContent = `${png.width} × ${png.height} pixels`;
            $('previews').hidden = false;
            exportStatus.textContent = `Captured at ${capturedWidth} px · ${capturedTheme} · SVG ${svg.width} × ${svg.height}. Resizing now keeps this snapshot unchanged.`;
        } catch (cause) {
            if (revision === token) exportStatus.textContent = `Export failed: ${cause.message}`;
        } finally {
            busy = false;
            controls();
        }
    });
    $('download-svg').addEventListener('click', () => { if (valid && snapshot) download(snapshot.svgUrl, 'fulfillment.svg'); });
    $('download-png').addEventListener('click', () => { if (valid && snapshot) download(snapshot.pngUrl, 'fulfillment.png'); });

    const observer = new ResizeObserver(dimensions);
    observer.observe($('diagram-width'));
    window.addEventListener('pagehide', () => { diagram?.destroy(); observer.disconnect(); clearSnapshot(); });
    window.addEventListener('pageshow', event => { if (event.persisted) { observer.observe($('diagram-width')); diagram = undefined; updateSource(); } });
    updateWidth();
    updateSource();
}
