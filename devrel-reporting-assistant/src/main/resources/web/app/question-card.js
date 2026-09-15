import {LitElement, html} from 'lit';
import {keyed} from 'lit/directives/keyed.js';

export class QuestionCard extends LitElement {
    static properties = {request: {attribute: false}, values: {attribute: false}, busy: {type: Boolean}, error: {type: String}};
    createRenderRoot() { return this; }

    control(field) {
        const value = this.values?.[field.id] ?? '';
        const shared = field.id;
        if (field.type === 'select' || field.type === 'boolean') {
            const options = field.type === 'boolean' ? ['true', 'false'] : field.options;
            return html`<select id=${shared} name=${shared} required .value=${String(value)}>
                <option value="" disabled>Choose an answer</option>
                ${options.map(option => html`<option value=${option} ?selected=${String(value) === option}>${option === 'true' ? 'Yes' : option === 'false' ? 'No' : option}</option>`)}
            </select>`;
        }
        if (field.type === 'textarea') return html`<textarea id=${shared} name=${shared} required maxlength="4000" rows="3" .value=${String(value)}></textarea>`;
        const numeric = ['integer', 'number'].includes(field.type);
        return html`<input id=${shared} name=${shared} type=${numeric ? 'number' : field.type}
            required maxlength="4000" min=${numeric ? '0' : ''} step=${field.type === 'integer' ? '1' : 'any'} .value=${String(value)}>`;
    }

    submit(event) {
        event.preventDefault();
        const data = new FormData(event.target);
        const values = {};
        for (const field of this.request.fields) {
            const raw = data.get(field.id);
            values[field.id] = field.type === 'boolean' ? raw === 'true'
                : ['integer', 'number'].includes(field.type) ? Number(raw) : raw;
        }
        this.dispatchEvent(new CustomEvent('answer', {detail: values, bubbles: true}));
    }

    render() {
        if (!this.request) return html``;
        return keyed(this.request.id, html`<section class="question panel" aria-label="Bob's question">
            <div class="eyebrow">Your next step</div>
            <h2>${this.request.question}</h2>
            <form @submit=${this.submit}>
                <fieldset ?disabled=${this.busy}>
                    ${this.request.fields.map(field => html`<div class="field">
                        <label for=${field.id}>${field.label}</label>
                        ${this.control(field)}
                        <small>${field.reason}</small>
                    </div>`)}
                </fieldset>
                ${this.error ? html`<p class="error" role="alert">${this.error}</p>` : ''}
                <div class="actions"><button type="submit" ?disabled=${this.busy}>${this.busy ? 'Sending…' : 'Send answers'}</button>
                    <button type="button" class="quiet" @click=${() => this.dispatchEvent(new CustomEvent('cancel-form', {bubbles: true}))}>Cancel and keep draft</button></div>
                <small>Answers are added to your draft. Bob will check what is still needed.</small>
            </form>
        </section>`);
    }
}
customElements.define('question-card', QuestionCard);
