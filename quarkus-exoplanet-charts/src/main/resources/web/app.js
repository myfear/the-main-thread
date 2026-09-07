import {
    Chart,
    BarController, DoughnutController, ScatterController,
    BarElement, ArcElement, PointElement,
    CategoryScale, LinearScale, LogarithmicScale,
    Legend, Tooltip
} from 'chart.js';

Chart.register(
    BarController, DoughnutController, ScatterController,
    BarElement, ArcElement, PointElement,
    CategoryScale, LinearScale, LogarithmicScale,
    Legend, Tooltip
);

const form = document.querySelector('#filters');
const status = document.querySelector('#status');
const results = document.querySelector('#results');
const instances = new Map();
const number = new Intl.NumberFormat('en-US');
const controls = [...form.elements];
let busy = false;
let current;

Chart.defaults.color = '#a8bacd';
Chart.defaults.borderColor = '#283646';
Chart.defaults.font.family = 'system-ui, sans-serif';
Chart.defaults.plugins.legend.labels.boxWidth = 10;

for (const name of ['from', 'to']) form.elements[name].max = new Date().getFullYear();

function table(caption, headings, rows) {
    const table = document.createElement('table');
    table.createCaption().textContent = caption;
    const header = table.createTHead().insertRow();
    for (const heading of headings) {
        const cell = document.createElement('th');
        cell.scope = 'col';
        cell.textContent = heading;
        header.append(cell);
    }
    const body = table.createTBody();
    for (const values of rows) {
        const row = body.insertRow();
        for (const value of values) row.insertCell().textContent = value;
    }
    return table;
}

function renderTables(data) {
    const target = document.querySelector('#tables');
    target.replaceChildren();
    const annual = data.discoveries.data;
    target.append(table('Discoveries by year', ['Year', ...annual.datasets.map(d => d.label)],
        annual.labels.map((year, i) => [year, ...annual.datasets.map(d => d.data[i])])));
    target.append(table('Discovery methods', ['Method', 'Planets'],
        data.methods.data.labels.map((method, i) => [method, data.methods.data.datasets[0].data[i]])));
    target.append(table('Planets shown in the scatter plot', ['Planet', 'Method group', 'Period (days)', 'Radius (Earth radii)'],
        data.sizes.data.datasets.flatMap(d => d.data.map(p => [p.name, d.label, p.x, p.y]))));
}

async function load() {
    if (busy || !form.reportValidity()) return;
    const params = new URLSearchParams(new FormData(form));
    busy = true;
    controls.forEach(control => control.disabled = true);
    results.hidden = true;
    status.className = '';
    status.textContent = params.get('source') === 'live'
        ? 'Loading NASA data… the first request can take up to a minute.' : 'Loading the bundled snapshot…';
    try {
        const response = await fetch(`/api/dashboard?${params}`);
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
        current = data;
        document.querySelector('#selected').textContent = number.format(data.selected);
        document.querySelector('#plotted').textContent = number.format(data.plotted);
        document.querySelector('#excluded').textContent = number.format(data.excludedFromScatter);
        results.hidden = false;

        for (const name of ['discoveries', 'methods', 'sizes']) {
            instances.get(name)?.destroy();
            const config = structuredClone(data[name]);
            config.options.plugins ??= {};
            if (name === 'sizes') {
                config.options.plugins.tooltip = { callbacks: {
                    label: context => `${context.raw.name}: ${context.raw.x} days, ${context.raw.y} Earth radii`
                }};
            }
            if (name === 'discoveries') {
                config.options.onClick = (_event, elements) => {
                    if (busy || !elements.length) return;
                    const year = config.data.labels[elements[0].index];
                    form.elements.from.value = year;
                    form.elements.to.value = year;
                    load();
                };
            }
            instances.set(name, new Chart(document.getElementById(name), config));
        }
        document.querySelector('#provenance').textContent =
            `${data.source === 'live' ? 'Live catalogue (cached for one hour)' : 'Bundled snapshot'} · retrieved ${data.retrievedAt} · ${number.format(data.catalogueTotal)} total records · ${data.unknownYear} without a discovery year.`;
        if (document.querySelector('details').open) renderTables(data);
        status.textContent = data.selected === 0 ? 'No planets in this year range.'
            : `Showing ${data.from}–${data.to}.${data.to === new Date().getFullYear() ? ' The current year is incomplete.' : ''}`;
    } catch (error) {
        results.hidden = true;
        status.className = 'error';
        status.textContent = error.message;
    } finally {
        busy = false;
        controls.forEach(control => control.disabled = false);
    }
}

form.addEventListener('submit', event => { event.preventDefault(); load(); });
document.querySelector('#reset').addEventListener('click', () => {
    form.elements.from.value = 1992;
    form.elements.to.value = 2025;
    load();
});
document.querySelector('details').addEventListener('toggle', event => {
    if (event.target.open && current) renderTables(current);
});
load();
