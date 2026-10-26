'use strict';
const state = { project: null, conversation: null, facts: [], proposals: [], messages: [], tools: [], busy: false };
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const short = value => String(value).slice(0, 8);
const plural = (count, word) => `${count} ${word}${count === 1 ? '' : 's'}`;

async function api(path, body) {
    const response = await fetch('/api' + path, body === undefined ? {} : {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || data.message || 'Request failed. Check the input and try again.');
    return data;
}
function announce(message, error = false) {
    $('status').textContent = message;
    $('status').classList.toggle('error', error);
}
function setBusy(busy, label = 'Ready') {
    state.busy = busy;
    document.querySelectorAll('button, select').forEach(control => { control.disabled = busy; });
    $('message').disabled = busy || !state.conversation;
    $('send').disabled = busy || !state.conversation;
    $('turn-state').textContent = label;
    $('composer').setAttribute('aria-busy', busy);
}
async function guarded(action, label) {
    if (state.busy) return;
    setBusy(true, label);
    try { await action(); } catch (error) { announce(error.message, true); }
    finally { setBusy(false); }
}
async function loadConversations(preferred) {
    const conversations = await api('/conversations?projectId=' + state.project);
    $('conversation').innerHTML = conversations.length ? conversations.map(c => `<option value="${c.id}">${esc(c.title)}</option>`).join('') : '<option value="">Create a conversation</option>';
    state.conversation = conversations.some(c => c.id === preferred) ? preferred : conversations[0]?.id || null;
    $('conversation').value = state.conversation || '';
    await refresh();
}
async function refresh() {
    const [facts, proposals, messages, tools] = await Promise.all([
        api('/memories?projectId=' + state.project), api('/proposals?projectId=' + state.project),
        state.conversation ? api('/conversations/' + state.conversation + '/messages') : [],
        state.conversation ? api('/tool-invocations?conversationId=' + state.conversation) : []
    ]);
    Object.assign(state, { facts, proposals, messages, tools });
    render();
}
function render() {
    $('fact-count').textContent = `${state.facts.length} active`;
    $('pending-count').textContent = `${state.proposals.length} pending`;
    $('messages').innerHTML = state.messages.length ? state.messages.map(m => {
        const source = m.role === 'USER' ? m.id : m.replyToId;
        const calls = state.tools.filter(t => t.sourceMessageId === source).length;
        const mutations = state.tools.filter(t => t.sourceMessageId === source && t.toolName !== 'memory_search');
        const pending = state.proposals.filter(p => p.sourceMessageId === source).length;
        const rejected = mutations.length && !mutations.some(t => t.outcome === 'SUCCESS');
        const applicationState = m.role !== 'ASSISTANT' ? '' : pending ? `<p class="application-state">Application state: ${plural(pending, 'change')} pending your approval.</p>` : rejected ? '<p class="application-state warning">Application state: no change proposed. The tool rejected this attempt. Ask the assistant to try again.</p>' : '';
        return `<article class="message ${m.state === 'FAILED' ? 'failed' : ''}" id="message-${m.id}"><strong>${m.role === 'USER' ? 'You' : 'Assistant'}</strong><p>${esc(m.content)}</p>${applicationState}<small>Message ${short(m.id)} · ${m.state === 'FAILED' ? 'Turn failed; proposals rejected' : m.role === 'ASSISTANT' ? (calls ? plural(calls, 'tool call') : 'No tool call') : m.state.toLowerCase()}</small></article>`;
    }).join('') : '<p class="empty">Start with the offsite location, date, and headcount. The assistant can propose a separate saved fact for each detail.</p>';
    $('messages').scrollTop = $('messages').scrollHeight;
    $('facts').innerHTML = state.facts.length ? state.facts.map(m => `<article class="fact"><div class="kind">${esc(m.kind)}</div><p>${esc(m.text)}</p><div class="fact-id">${m.id}</div><details data-history="${m.id}"><summary>Source and history</summary><button class="text-button" data-source="${m.sourceConversationId}" data-message="${m.sourceMessageId}">Open source message ${short(m.sourceMessageId)}</button><div class="history-content">Open to load versions.</div></details><button class="text-button" data-forget="${m.id}">Propose removal</button></article>`).join('') : '<p class="empty">No saved facts. Ask the assistant to remember a detail, then approve the proposal.</p>';
    $('proposals').innerHTML = state.proposals.length ? state.proposals.map(p => {
        const target = state.facts.find(m => m.id === p.targetMemoryId);
        const verb = { REMEMBER: 'Save fact', SUPERSEDE: 'Replace fact', FORGET: 'Remove saved fact' }[p.operation];
        const reason = p.reason?.trim() !== p.proposedText?.trim() ? p.reason : '';
        return `<article class="proposal"><div class="kind">${verb}</div>${target ? `<p class="old-text">${p.operation === 'FORGET' ? 'Remove:' : 'Current:'} ${esc(target.text)}</p>` : ''}${p.proposedText ? `<p>${esc(p.proposedText)}</p>` : ''}${reason ? `<p class="section-help">${esc(reason)}</p>` : ''}${p.operation === 'FORGET' ? '<p class="section-help">Saved versions and related proposal text will also be removed. Conversation text is retained.</p>' : ''}<p class="pending-label">Pending approval</p><button class="text-button" data-source="${p.sourceConversationId}" data-message="${p.sourceMessageId}">Source message ${short(p.sourceMessageId)}</button><div class="proposal-actions"><button data-decision="approve" data-proposal="${p.id}">Approve ${p.operation === 'FORGET' ? 'removal' : 'change'}</button><button class="secondary" data-decision="reject" data-proposal="${p.id}">Reject</button></div></article>`;
    }).join('') : '<p class="empty">No pending changes. Proposals appear here when the assistant calls a memory tool.</p>';
    $('activity').innerHTML = state.tools.length ? state.tools.slice().reverse().map(t => `<li><code>${esc(t.toolName)}</code><small class="outcome ${t.outcome === 'SUCCESS' ? 'success' : t.outcome === 'ZERO_RESULTS' ? 'neutral' : t.outcome === 'REJECTED' ? 'warning' : 'error'}">${esc(t.outcome.replaceAll('_', ' '))} · ${t.durationMs} ms</small><small>${esc(t.description)} · source ${short(t.sourceMessageId)}</small></li>`).join('') : '<li class="empty">No tool calls in this conversation. A reply without a tool call has not searched saved facts.</li>';
    document.querySelectorAll('[data-history]').forEach(detail => detail.addEventListener('toggle', async () => {
        if (!detail.open || detail.dataset.loaded) return;
        try {
            const history = await api('/memories/' + detail.dataset.history + '/history?projectId=' + state.project);
            detail.querySelector('.history-content').innerHTML = history.map(m => `<div class="history-entry"><p>${esc(m.text)}</p><small>${esc(m.status)} · ${new Date(m.createdAt).toLocaleString()}</small></div>`).join('');
            detail.dataset.loaded = 'true';
        } catch (error) { detail.querySelector('.history-content').textContent = error.message; }
    }));
}
$('project').addEventListener('change', () => guarded(async () => {
    state.project = $('project').value;
    $('message').value = '';
    await loadConversations();
    announce('Project changed. Saved facts are scoped to this project.');
}, 'Loading project…'));
$('conversation').addEventListener('change', () => guarded(async () => {
    state.conversation = $('conversation').value || null;
    $('message').value = '';
    await refresh();
    announce('Conversation loaded. Each conversation has its own context window.');
}, 'Loading conversation…'));
$('new-conversation').addEventListener('submit', event => {
    event.preventDefault();
    guarded(async () => {
        const c = await api('/conversations', { projectId: state.project, title: $('title').value });
        $('title').value = '';
        await loadConversations(c.id);
        announce('New conversation created with an empty context window.');
    }, 'Creating conversation…').then(() => $('message').focus());
});
$('composer').addEventListener('submit', event => {
    event.preventDefault();
    guarded(async () => {
        const text = $('message').value;
        announce('Waiting for the local model. Tool calls and proposals will appear when the turn finishes.');
        let result;
        try {
            result = await api('/conversations/' + state.conversation + '/messages', { text, requestId: crypto.randomUUID() });
            $('message').value = '';
        } finally { await refresh(); }
        const calls = state.tools.filter(t => t.sourceMessageId === result.userMessageId);
        const rejected = calls.some(t => t.toolName !== 'memory_search') && !calls.some(t => t.toolName !== 'memory_search' && t.outcome === 'SUCCESS');
        announce(rejected ? 'No change proposed. The tool rejected this attempt; ask the assistant to try again.' : result.toolCalls ? `Turn complete. ${plural(result.toolCalls, 'tool call')}; check the results and pending changes.` : 'Turn complete. No tool call; the assistant did not search or propose a saved fact.');
    }, 'Assistant responding…');
});
document.addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button || state.busy) return;
    if (button.dataset.decision) guarded(async () => {
        const proposal = state.proposals.find(p => p.id === button.dataset.proposal);
        await api('/proposals/' + proposal.id + '/' + button.dataset.decision, { projectId: state.project, expectedVersion: proposal.version });
        await refresh();
        announce(button.dataset.decision === 'approve' ? 'Change approved. Saved facts updated.' : 'Proposal rejected. Saved facts unchanged.');
    }, 'Applying decision…');
    if (button.dataset.forget) {
        const fact = state.facts.find(m => m.id === button.dataset.forget);
        $('message').value = `Please forget this saved fact: ${fact.text}`;
        $('message').focus();
        announce('Removal request added to the message. Send it, then review the proposal.');
    }
    if (button.dataset.source) guarded(async () => {
        await loadConversations(button.dataset.source);
        document.getElementById('message-' + button.dataset.message)?.scrollIntoView({ block: 'center' });
        announce('Source conversation opened.');
    }, 'Loading source…');
});
(async () => {
    try {
        const projects = await api('/projects');
        $('project').innerHTML = projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('');
        state.project = projects.find(p => p.name === 'Team Offsite')?.id || projects[0].id;
        $('project').value = state.project;
        await loadConversations();
        announce('Ready. Start a conversation or review this project’s saved facts.');
    } catch (error) { announce(error.message, true); }
    finally { setBusy(false); }
})();
