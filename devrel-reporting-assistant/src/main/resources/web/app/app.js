import {LitElement, html} from 'lit';
import {api, readEvents} from './stream.js';
import './question-card.js';
import './report-summary.js';
import './styles.css';

class ReportAssistant extends LitElement {
    static properties = {report: {state: true}, error: {state: true}, formError: {state: true}, busy: {state: true}, sending: {state: true}, loading: {state: true}};
    constructor() { super(); this.loading = true; this.cursor = 0; this.error = ''; }
    createRenderRoot() { return this; }
    connectedCallback() { super.connectedCallback(); this.restore(); }
    disconnectedCallback() { this.controller?.abort(); super.disconnectedCallback(); }

    async restore() {
        try {
            this.report = await api();
            this.cursor = this.report.cursor;
            if (this.report.running) this.reconnect();
        } catch (error) { if (error.status !== 404) this.error = error.message; }
        finally { this.loading = false; }
    }

    async create(event) {
        event.preventDefault();
        this.busy = true;
        try {
            const data = new FormData(event.target);
            this.report = await api('', {month: data.get('month'), author: data.get('author')}, 'POST');
            this.cursor = this.report.cursor;
            this.error = '';
        } catch (error) { this.error = error.message; }
        finally { this.busy = false; }
    }

    event(data, sequence) {
        if (sequence != null && sequence <= this.cursor) return;
        if (sequence != null) this.cursor = sequence;
        if (data.type === 'STATE_SNAPSHOT') this.report = data.snapshot;
        if (data.type === 'TEXT_MESSAGE_START') {
            this.report = {...this.report, messages: [...this.report.messages, {id: data.messageId, role: data.role, content: ''}]};
        }
        if (data.type === 'TEXT_MESSAGE_CONTENT') {
            this.report = {...this.report, messages: this.report.messages.map(message => message.id === data.messageId ? {...message, content: message.content + data.delta} : message)};
        }
        if (data.type === 'RUN_ERROR') this.error = data.message;
        if (data.type === 'RUN_ERROR' || data.type === 'RUN_FINISHED') this.report = {...this.report, running: false};
    }

    async send(event) {
        event.preventDefault();
        const input = event.target.elements.namedItem('notes');
        const prompt = input.value.trim();
        if (!prompt || this.report.running || this.sending) return;
        this.sending = true;
        this.error = '';
        this.controller?.abort();
        this.controller = new AbortController();
        try {
            const body = {threadId: this.report.id, runId: crypto.randomUUID(), messages: [{id: crypto.randomUUID(), role: 'user', content: prompt}], tools: [], context: [], state: {}, forwardedProps: {}};
            input.value = '';
            await readEvents('/api/report/agent', {method: 'POST', body: JSON.stringify(body), signal: this.controller.signal}, (data, id) => this.event(data, id));
            if (this.report.running) await this.reconnect();
        } catch (error) {
            if (error.name !== 'AbortError') { this.error = error.message; await this.restore(); }
        } finally { this.sending = false; }
    }

    async reconnect() {
        this.controller?.abort();
        this.controller = new AbortController();
        try {
            await readEvents(`/api/report/events?after=${this.cursor}&runId=${encodeURIComponent(this.report.runId)}`,
                {signal: this.controller.signal}, (data, id) => this.event(data, id));
        } catch (error) { if (error.name !== 'AbortError') this.error = 'Connection interrupted. Reconnect to restore the current report.'; }
    }

    async answer(event) {
        this.busy = true;
        this.formError = '';
        const form = this.report.pendingForm;
        try {
            // The stream is authoritative during a run; avoid overwriting newer events with an older HTTP response.
            await api(`/forms/${form.id}/answers`, {expectedRevision: form.revision, values: event.detail}, 'POST');
        } catch (error) { this.formError = error.message; }
        finally { this.busy = false; }
    }

    async stop() {
        this.busy = true;
        try {
            this.report = await api('/stop', {}, 'POST');
            this.controller?.abort();
            this.sending = false;
            this.error = '';
            this.formError = '';
        } catch (error) { this.error = error.message; }
        finally { this.busy = false; }
    }

    async save() {
        try { this.report = await api('/save', {expectedRevision: this.report.revision}, 'POST'); }
        catch (error) { this.error = error.message; }
    }

    async reset() {
        if (!confirm('Start a new report? The current in-memory draft will be cleared. Download a saved report first.')) return;
        this.controller?.abort();
        try { await api('', undefined, 'DELETE'); this.report = null; this.cursor = 0; this.error = ''; this.sending = false; }
        catch (error) { this.error = error.message; }
    }

    example(kind) {
        const date = this.report.today.startsWith(this.report.month) ? this.report.today : `${this.report.month}-10`;
        const notes = kind === 'workshop'
            ? `On ${date} I ran a two-hour hybrid workshop at Runtime Days about Orbit AI Toolkit. Around 50 people attended. I travelled there and still owe someone an example project.`
            : `On ${date} I published a recorded video about Forge Runtime at https://video.example.org/watch/runtime-intro. Please collect the views later. The video explains how to diagnose slow startup; impact is not yet known. There is no outstanding follow-up.`;
        this.querySelector('[name=notes]').value = notes;
        this.querySelector('[name=notes]').focus();
    }

    render() {
        const r = this.report;
        return html`<header class="masthead"><div class="brand"><span class="mark" aria-hidden="true">fn</span><div><strong>Field Notes</strong><span>Developer advocacy reports</span></div></div>
            ${r ? html`<div class="header-meta">${r.month} · ${r.values.author}<button class="quiet" @click=${this.reset}>New report</button></div>` : html`<span class="demo-label">A fictional reporting demo</span>`}</header>
            <main>
                ${this.error ? html`<div class="error panel" role="alert">${this.error} ${r?.running ? html`<button class="quiet" @click=${this.restore}>Reconnect</button>` : ''}</div>` : ''}
                ${this.loading ? html`<p>Loading your report…</p>` : !r ? html`<section class="welcome panel">
                    <div class="eyebrow">Less form filling. Better field notes.</div>
                    <h1>Tell Bob what you did.<br>Work through the details together.</h1>
                    <p>Start with rough notes. Bob asks the relevant follow-up questions, and your report takes shape alongside the conversation.</p>
                    <form @submit=${this.create}><div class="setup-grid"><div class="field"><label for="month">Reporting month</label><input id="month" name="month" type="month" required .value=${new Date().toISOString().slice(0, 7)}></div>
                        <div class="field"><label for="author">Advocate</label><select id="author" name="author"><option>Taylor Quinn</option><option>Jordan Vale</option><option>Morgan Reed</option></select></div></div>
                        <button ?disabled=${this.busy}>Start an activity report</button></form>
                    <small>Fictional people and policies. Reports stay in memory until you download them.</small>
                </section>` : html`<div class="workspace"><section class="conversation">
                    <div class="section-heading"><div><div class="eyebrow">One activity at a time</div><h1>Work through your report</h1></div>
                        ${r.running ? html`<span class="live-status" role="status">${r.pendingForm ? 'Waiting for your answer' : 'Bob is working…'}</span>` : ''}</div>
                    ${!r.messages.length ? html`<div class="intro"><p>What did you work on? Include any dates, numbers, and links you already have.</p><div class="actions"><button class="secondary" @click=${() => this.example('workshop')}>Workshop example</button><button class="secondary" @click=${() => this.example('video')}>Video example</button></div></div>` : ''}
                    <div class="messages" aria-live="polite">${r.messages.map(message => html`<article class="message ${message.role}"><strong>${message.role === 'user' ? 'You' : 'Bob'}</strong><p>${message.content}</p></article>`)}</div>
                    <question-card .request=${r.pendingForm} .values=${r.values} .busy=${this.busy} .error=${this.formError} @answer=${this.answer} @cancel-form=${this.stop}></question-card>
                    ${r.status !== 'saved' ? html`<form class="composer panel" @submit=${this.send}><label for="notes">${r.messages.length ? 'Add a detail or correction' : 'Your activity notes'}</label>
                        <textarea id="notes" name="notes" rows="3" maxlength="12000" required ?disabled=${r.running || this.sending} placeholder="I ran a workshop last week…"></textarea>
                        <div class="actions"><button ?disabled=${r.running || this.sending}>Send to Bob</button>${r.running ? html`<button type="button" class="quiet" @click=${this.stop}>Stop to make a correction</button>` : ''}</div></form>` : html`<div class="panel success">Your report is saved. Download a copy from the summary before starting another.</div>`}
                </section><report-summary .report=${r} @save-report=${this.save}></report-summary></div>`}
                <footer>Field Notes · A Quarkus + Bob demo · No external reporting system is connected.</footer>
            </main>`;
    }
}
customElements.define('report-assistant', ReportAssistant);
