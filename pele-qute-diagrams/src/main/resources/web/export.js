const SVG_NS = 'http://www.w3.org/2000/svg';
const PAINT_PROPERTIES = [
    'fill', 'fill-opacity', 'stroke', 'stroke-width', 'stroke-opacity',
    'stroke-dasharray', 'stroke-linecap', 'stroke-linejoin', 'color',
    'font-family', 'font-size', 'font-weight', 'font-style', 'letter-spacing',
    'word-spacing', 'text-anchor', 'dominant-baseline', 'rx', 'ry'
];

export function snapshotSvg(element) {
    const clone = element.cloneNode(true);
    const viewBox = element.viewBox.baseVal;
    const width = Math.ceil(viewBox.width);
    const height = Math.ceil(viewBox.height);
    if (!(width > 0 && height > 0)) {
        throw new Error('The diagram has no exportable dimensions. Try restoring the sample.');
    }

    // Resolve inherited colors and fonts while the original is still in the page.
    const originals = [element, ...element.querySelectorAll('*')];
    const copies = [clone, ...clone.querySelectorAll('*')];
    originals.forEach((original, index) => {
        const style = getComputedStyle(original);
        for (const property of PAINT_PROPERTIES) {
            copies[index].style.setProperty(property, style.getPropertyValue(property));
        }
        // Pele's private variables may refer to these public theme variables.
        for (const property of style) {
            if (property.startsWith('--pele-')) {
                copies[index].style.setProperty(property, style.getPropertyValue(property));
            }
        }
    });

    clone.setAttribute('xmlns', SVG_NS);
    clone.setAttribute('width', String(width));
    clone.setAttribute('height', String(height));
    clone.style.removeProperty('max-width');
    clone.style.removeProperty('height');
    const background = document.createElementNS(SVG_NS, 'rect');
    background.setAttribute('x', String(viewBox.x));
    background.setAttribute('y', String(viewBox.y));
    background.setAttribute('width', String(viewBox.width));
    background.setAttribute('height', String(viewBox.height));
    background.setAttribute('fill', getComputedStyle(element).getPropertyValue('--pele-bg').trim() || '#ffffff');
    clone.prepend(background);

    const text = new XMLSerializer().serializeToString(clone);
    return { text, width, height, blob: new Blob([text], { type: 'image/svg+xml;charset=utf-8' }) };
}

export async function rasterize(svg, density) {
    const width = svg.width * density;
    const height = svg.height * density;
    if (width > 8192 || height > 8192 || width * height > 16_000_000) {
        throw new Error('This image is too large for export. Choose 1× or simplify the diagram.');
    }
    const url = URL.createObjectURL(svg.blob);
    try {
        const image = new Image();
        image.src = url;
        await image.decode();
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Canvas is unavailable in this browser.');
        context.drawImage(image, 0, 0, width, height);
        const blob = await new Promise((resolve, reject) => {
            canvas.toBlob(result => result ? resolve(result) : reject(new Error('PNG encoding failed. Try again.')), 'image/png');
        });
        return { blob, width, height };
    } finally {
        URL.revokeObjectURL(url);
    }
}

export function download(url, name) {
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.append(link);
    link.click();
    link.remove();
}
