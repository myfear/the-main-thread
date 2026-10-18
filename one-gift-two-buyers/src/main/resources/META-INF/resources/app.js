const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
let roundId = params.get('round');
let pending = 0;
let ready = false;
let refreshSequence = 0;
let runHadError = false;
const sent = new Set();
const responses = {};
$('mode').value = params.get('mode') === 'optimistic' ? 'optimistic' : 'unsafe';

async function request(path, body) {
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000)
  });
  const data = await response.json();
  return { status: response.status, body: data };
}

function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').className = error ? 'error' : '';
}

function controls() {
  $('new-round').disabled = pending > 0;
  $('mode').disabled = pending > 0 || !ready;
  $('run').disabled = pending > 0 || !ready || sent.size > 0;
  for (const buyer of ['first', 'second']) {
    $(`reserve-${buyer}`).disabled = !ready || sent.has(buyer);
    $(buyer).disabled = sent.has(buyer);
  }
  $('refresh').disabled = !ready;
}

function linkRound() {
  const url = new URL(location.href);
  url.searchParams.set('round', roundId);
  url.searchParams.set('mode', $('mode').value);
  history.replaceState(null, '', url);
  $('second-window').href = url;
}

function resetResults() {
  runHadError = false;
  sent.clear();
  for (const key of Object.keys(responses)) delete responses[key];
  for (const buyer of ['first', 'second']) {
    $(`result-${buyer}`).textContent = 'No request sent.';
    $(`result-${buyer}`).className = 'result';
  }
  $('payload').textContent = 'No responses yet.';
  $('strategy').textContent = $('mode').value === 'unsafe'
    ? 'Both requests can overwrite the same gift. Each buyer can receive a success response.'
    : 'The version check rejects a stale update. One buyer reserves the gift; the other receives a conflict.';
}

async function refresh() {
  const requestedRound = roundId;
  const sequence = ++refreshSequence;
  const isCurrent = () => requestedRound === roundId && sequence === refreshSequence;
  $('saved').textContent = 'Reading saved state…';
  try {
    const result = await request(`/api/demo/rounds/${requestedRound}`);
    if (!isCurrent()) return;
    if (result.status !== 200) throw new Error(result.body.message || `HTTP ${result.status}`);
    const gift = result.body[$('mode').value];
    $('saved').textContent = `${gift.reservedBy ? `Reserved by ${gift.reservedBy}.` : 'Available. No reservation saved.'} ${gift.version === null ? 'No version column.' : `Version ${gift.version}.`}`;
  } catch (error) {
    if (!isCurrent()) return;
    $('saved').textContent = 'Saved state unavailable. Refresh to read the current owner.';
    throw error;
  }
}

async function newRound() {
  refreshSequence++;
  ready = false;
  pending++;
  $('saved').textContent = 'Creating a fresh gift…';
  controls();
  notice('Creating a fresh gift…');
  try {
    const result = await request('/api/demo/rounds', {});
    if (result.status !== 201) throw new Error(result.body.message || `HTTP ${result.status}`);
    roundId = result.body.id;
    resetResults();
    linkRound();
    await refresh();
    ready = true;
    notice('Ready. Send both requests together, or use the individual buyer buttons within 10 seconds.');
  } catch (error) {
    notice(`${error.message} Start the app in dev mode, then select New round.`, true);
  } finally {
    pending--;
    controls();
  }
}

async function reserve(buyer) {
  const input = $(buyer);
  input.setCustomValidity(input.value.trim() ? '' : 'Enter a buyer label.');
  if (!input.reportValidity()) return;
  sent.add(buyer);
  pending++;
  controls();
  const output = $(`result-${buyer}`);
  output.className = 'result';
  output.textContent = 'Request in progress. Waiting for the other buyer…';
  notice('Both requests pause after reading the available gift.');
  let receivedResponse = false;
  try {
    const result = await request(`/api/demo/rounds/${roundId}/${$('mode').value}/reservations`, { buyer: input.value.trim() });
    receivedResponse = true;
    responses[buyer] = result;
    output.textContent = result.status === 200
      ? `HTTP 200 · Reservation confirmed for ${result.body.reservedBy}.`
      : `HTTP ${result.status} · ${result.body.message || 'Request rejected. Create a new round to try again.'}`;
    output.className = `result ${result.status === 200 ? 'success' : 'error'}`;
    $('payload').textContent = JSON.stringify(responses, null, 2);
    await refresh();
  } catch (error) {
    runHadError = true;
    if (receivedResponse) {
      notice(`Could not refresh the saved state: ${error.message}. The reservation response is shown above.`, true);
    } else {
      output.className = 'result error';
      output.textContent = `${error.message}. The reservation outcome is unknown.`;
      notice('No reservation response received. Refresh the saved state before trying again.', true);
    }
  } finally {
    pending--;
    controls();
    if (pending === 0 && !runHadError) notice('Compare each response with the saved owner. Select New round to repeat.');
  }
}

$('controls').addEventListener('submit', async (event) => {
  event.preventDefault();
  for (const buyer of ['first', 'second']) {
    $(buyer).setCustomValidity($(buyer).value.trim() ? '' : 'Enter a buyer label.');
  }
  if (!$('controls').reportValidity()) return;
  if ($('first').value.trim() === $('second').value.trim()) {
    notice('Use two different labels so you can distinguish the saved owner.', true);
    return;
  }
  await Promise.all(['first', 'second'].map(reserve));
});
for (const buyer of ['first', 'second']) {
  $(`reserve-${buyer}`).addEventListener('click', () => reserve(buyer));
  $(buyer).addEventListener('input', () => $(buyer).setCustomValidity(''));
}
$('new-round').addEventListener('click', newRound);
$('refresh').addEventListener('click', () => refresh().catch((error) => notice(error.message, true)));
$('mode').addEventListener('change', () => {
  resetResults();
  linkRound();
  controls();
  refresh().catch((error) => notice(error.message, true));
  notice('Strategy changed. Each strategy has a separate gift row in this round.');
});

resetResults();
controls();
if (roundId) {
  pending++;
  controls();
  refresh().then(() => {
    ready = true;
    linkRound();
    controls();
    notice('Shared round loaded. Use one buyer here and the other in the first window.');
  }).catch((error) => notice(`${error.message} Select New round to start again.`, true))
    .finally(() => {
      pending--;
      controls();
    });
} else {
  newRound();
}
