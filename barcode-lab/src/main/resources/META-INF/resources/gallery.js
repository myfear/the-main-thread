'use strict';

for (const form of document.querySelectorAll('form[data-format]')) {
    const card = form.closest('.card');
    const status = card.querySelector('.status');
    const symbol = card.querySelector('.symbol');
    const image = symbol.querySelector('img');
    const download = card.querySelector('.download');
    const dimensions = card.querySelector('.dimensions');
    let objectUrl;

    const showDimensions = () => {
        if (image.naturalWidth > 0) {
            dimensions.textContent = `${image.naturalWidth} × ${image.naturalHeight} px / PNG`;
        }
    };
    image.addEventListener('load', showDimensions);
    if (image.complete) showDimensions();

    form.addEventListener('input', () => {
        symbol.hidden = true;
        download.hidden = true;
        status.classList.remove('error');
        status.textContent = 'Payload or settings changed. Generate to refresh the image.';
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = form.querySelector('button');
        button.disabled = true;
        status.classList.remove('error');
        status.textContent = 'Generating…';
        symbol.hidden = true;
        download.hidden = true;
        try {
            const data = new FormData(form);
            const value = data.get('value');
            data.delete('value');
            const params = new URLSearchParams(data);
            const response = await fetch(`/barcodes/${form.dataset.format}?${params}`, {
                method: 'POST',
                headers: { Accept: 'image/png, application/problem+json', 'Content-Type': 'text/plain; charset=UTF-8' },
                body: value
            });
            if (!response.ok) {
                const problem = await response.json();
                throw new Error(problem.detail || 'Could not generate this barcode.');
            }
            const blob = await response.blob();
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            objectUrl = URL.createObjectURL(blob);
            image.src = objectUrl;
            image.alt = `${card.querySelector('h2').textContent} encoding the current payload`;
            download.href = objectUrl;
            symbol.hidden = false;
            download.hidden = false;
            const width = response.headers.get('X-Barcode-Width');
            const height = response.headers.get('X-Barcode-Height');
            dimensions.textContent = `${width} × ${height} px / PNG`;
            status.textContent = 'Encoded successfully. Use the tests to check decoding; try your scanner too.';
        } catch (error) {
            status.classList.add('error');
            status.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });
}
