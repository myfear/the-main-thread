(() => {
    const workflow = document.getElementById('workflow');
    const status = document.getElementById('status');
    let busy = false;
    let focusAfterSwap = true;
    const ours = event => event.detail.target === workflow;

    document.body.addEventListener('htmx:beforeRequest', event => {
        if (!ours(event)) return;
        if (busy) {
            event.preventDefault();
            return;
        }
        busy = true;
        workflow.setAttribute('aria-busy', 'true');
        workflow.querySelectorAll('button').forEach(button =>
            button.setAttribute('aria-disabled', 'true'));
        status.textContent = event.detail.elt.dataset.pending || 'Working…';
    });

    document.body.addEventListener('htmx:beforeSwap', event => {
        if (!ours(event)) return;
        focusAfterSwap = workflow.contains(document.activeElement)
            || document.activeElement === document.body;
        if (event.detail.xhr.status === 422
                && event.detail.xhr.getResponseHeader('X-Workflow-Fragment') === 'true') {
            event.detail.shouldSwap = true;
            event.detail.isError = false;
        }
    });

    document.body.addEventListener('htmx:afterRequest', event => {
        if (!ours(event)) return;
        busy = false;
        workflow.setAttribute('aria-busy', 'false');
        workflow.querySelectorAll('button').forEach(button =>
            button.removeAttribute('aria-disabled'));
        status.textContent = event.detail.successful ? ''
            : 'The request could not be completed. Your entries are still here. Try again.';
    });

    document.body.addEventListener('htmx:afterSettle', event => {
        if (!ours(event)) return;
        const section = workflow.querySelector('[data-title]');
        document.title = section.dataset.title + ' — Equipment requests';
        // A slow response must not pull someone back after they leave the form.
        if (focusAfterSwap && (workflow.contains(document.activeElement)
                || document.activeElement === document.body)) {
            workflow.querySelector('[data-focus]')?.focus();
        } else {
            status.textContent = section.dataset.title + '. Return to the request to continue.';
        }
    });

    workflow.addEventListener('click', event => {
        const link = event.target.closest('.errors a');
        if (!link) return;
        const field = document.getElementById(link.hash.slice(1));
        if (field) {
            event.preventDefault();
            field.focus();
        }
    });
})();
