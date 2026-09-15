import {LitElement, html} from 'lit';

export class ReportSummary extends LitElement {
    static properties = {report: {attribute: false}};
    createRenderRoot() { return this; }
    render() {
        const r = this.report;
        if (!r) return html``;
        const fields = Object.fromEntries(r.catalog.map(f => [f.id, f]));
        return html`<aside class="panel summary" aria-label="Report summary">
            <div class="eyebrow">The report so far</div>
            <h2>${r.values.title || 'Untitled activity'}</h2>
            <span class="badge ${r.status}">${r.status === 'saved' ? 'Saved' : r.status === 'ready' ? 'Ready for review' : 'Needs details'}</span>
            <dl>${Object.entries(r.values).map(([key, value]) => html`<div><dt>${fields[key]?.label || key}</dt>
                <dd>${typeof value === 'boolean' ? (value ? 'Yes' : 'No') : String(value)}</dd></div>`)}</dl>
            ${r.totalAttendees !== null ? html`<p class="total">Total attendance <strong>${r.totalAttendees}</strong></p>` : ''}
            ${r.metricsStatus ? html`<p class="notice">Views: ${r.metricsStatus}. No count has been assumed.</p>` : ''}
            ${r.clearedFields?.length ? html`<p class="notice">No longer applicable: ${r.clearedFields.map(id => fields[id]?.label).join(', ')}.</p>` : ''}
            ${r.validation.issues.length ? html`<details open><summary>${r.validation.issues.length} details to resolve</summary>
                <ul class="requirements">${r.validation.issues.map(issue => html`<li><strong>${fields[issue.field]?.label}</strong><span>${issue.message}</span></li>`)}</ul></details>` : ''}
            ${r.status === 'ready' ? html`<p>Review every value, including any pending follow-up, before saving.</p>
                <button class="wide" ?disabled=${r.running} @click=${() => this.dispatchEvent(new CustomEvent('save-report', {bubbles: true}))}>${r.running ? 'Bob is finishing…' : 'Save report'}</button>` : ''}
            ${r.status === 'saved' ? html`<p>Saved for this demo session.</p><a class="button wide" href="/api/report/download" download="field-notes-report.json">Download JSON</a>` : ''}
        </aside>`;
    }
}
customElements.define('report-summary', ReportSummary);
