const grid = document.querySelector("#grid");
const sentinel = document.querySelector("#sentinel");
const statusEl = document.querySelector("#status");

const qEl = document.querySelector("#q");
const categoryEl = document.querySelector("#category");
const sortByEl = document.querySelector("#sortBy");
const applyBtn = document.querySelector("#apply");
const loadPrevBtn = document.querySelector("#loadPrev");

let nextCursor = null;
let prevCursor = null;
let loading = false;

function paramsBase() {
    const q = qEl.value.trim();
    const category = categoryEl.value.trim();
    const sortBy = sortByEl.value;

    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (category) p.set("category", category);
    p.set("sortBy", sortBy);

    return p;
}

function card(product) {
    const el = document.createElement("div");
    el.className = "card";
    el.innerHTML = `
    <div><strong>${escapeHtml(product.name)}</strong></div>
    <div class="muted">${escapeHtml(product.category)} · views ${product.viewCount ?? 0}</div>
    <div class="muted">${escapeHtml(product.description ?? "")}</div>
  `;
    return el;
}

function escapeHtml(s) {
    return String(s).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

async function load(direction) {
    if (loading) return;
    loading = true;

    try {
        statusEl.textContent = direction === "prev" ? "Loading previous..." : "Loading more...";

        const p = paramsBase();
        p.set("direction", direction);

        // When using a cursor, we need to extract the sort method from it
        // because the backend might have switched to relevance sorting for search queries
        let cursorToUse = null;
        if (direction === "next" && nextCursor) {
            cursorToUse = nextCursor;
        }
        if (direction === "prev" && prevCursor) {
            cursorToUse = prevCursor;
        }

        if (cursorToUse) {
            p.set("cursor", cursorToUse);
            // Extract sort from cursor by decoding the base64 payload
            // The cursor format is: base64(iv + encrypted(json))
            // We can't decrypt it, but the backend will handle sort detection
            // For now, we'll rely on the backend to use the cursor's sort method
        }

        const res = await fetch(`/api/products?${p.toString()}`);
        if (!res.ok) {
            const txt = await res.text();
            console.error(`API error: ${res.status}`, txt);
            statusEl.textContent = `Error: ${txt}`;
            throw new Error(`API error: ${res.status} ${txt}`);
        }

        const page = await res.json();

        nextCursor = page.nextCursor || null;
        prevCursor = page.prevCursor || null;

        loadPrevBtn.disabled = !page.hasPrev;

        if (direction === "prev") {
            const nodes = page.data.map(card);
            for (let i = nodes.length - 1; i >= 0; i--) {
                grid.prepend(nodes[i]);
            }
            statusEl.textContent = "Ready";
        } else {
            page.data.map(card).forEach(n => grid.appendChild(n));
            statusEl.textContent = page.hasNext ? "Ready" : "End reached";
        }
    } finally {
        loading = false;
    }
}

function resetAndLoad() {
    grid.innerHTML = "";
    nextCursor = null;
    prevCursor = null;
    loadPrevBtn.disabled = true;

    load("next");
}

applyBtn.addEventListener("click", resetAndLoad);
loadPrevBtn.addEventListener("click", () => load("prev"));

const observer = new IntersectionObserver(entries => {
    const hit = entries.some(e => e.isIntersecting);
    if (hit && nextCursor !== null) {
        load("next");
    }
}, { rootMargin: "200px" });

observer.observe(sentinel);

resetAndLoad();