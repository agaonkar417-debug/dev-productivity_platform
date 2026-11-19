// DevFlow AI frontend: plain JavaScript, no build step.
// All user-provided text is inserted with textContent (never innerHTML) to avoid XSS.
"use strict";

const $ = (sel) => document.querySelector(sel);

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (v === true) node.setAttribute(k, "");
    else if (v !== false && v != null) node.setAttribute(k, v);
  }
  for (const c of children.flat()) {
    if (c != null) node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

// ---------------------------------------------------------------- API helper
class ApiError extends Error {
  constructor(message, problems) {
    super(message);
    this.problems = problems || [];
  }
}

async function api(method, url, body, { raw = false } = {}) {
  const res = await fetch(url, {
    method,
    headers: body !== undefined ? { "Content-Type": "application/json" } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let detail = res.status + " " + res.statusText;
    let problems = [];
    try {
      const pd = await res.json();
      detail = pd.detail || detail;
      problems = pd.problems || [];
    } catch (_) { /* not JSON */ }
    throw new ApiError(detail, problems);
  }
  if (raw) return res;
  if (res.status === 204) return null;
  return res.json();
}

function showErrors(box, err) {
  box.replaceChildren(el("strong", {}, err.message));
  if (err.problems && err.problems.length) {
    box.append(el("ul", {}, err.problems.map((p) => el("li", {}, p))));
  }
  box.hidden = false;
}

let toastTimer;
function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), 2600);
}

const store = {
  get(key) { try { return localStorage.getItem("devflow." + key); } catch (_) { return null; } },
  set(key, v) { try { localStorage.setItem("devflow." + key, v); } catch (_) { /* storage unavailable */ } },
};

async function downloadResponse(res, fallbackName) {
  const cd = res.headers.get("Content-Disposition") || "";
  const match = /filename="?([^";]+)"?/.exec(cd);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = el("a", { href: url, download: match ? match[1] : fallbackName });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ---------------------------------------------------------------- tabs & health
const loaders = { generate: loadGenerate, kits: loadKitsView, history: loadHistory };

function showView(name) {
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === name));
  document.querySelectorAll(".view").forEach((v) => v.classList.toggle("active", v.id === "view-" + name));
  loaders[name]();
}
document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => showView(t.dataset.view)));

async function checkHealth() {
  const h = $("#health");
  try {
    const res = await fetch("/actuator/health");
    const data = await res.json();
    const db = data.components && data.components.db ? data.components.db.status : "?";
    h.textContent = "server " + data.status.toLowerCase() + " · database " + db.toLowerCase();
    h.className = "health " + (data.status === "UP" ? "up" : "down");
  } catch (_) {
    h.textContent = "server unreachable";
    h.className = "health down";
  }
}

// ---------------------------------------------------------------- GENERATE
const EXAMPLES = {
  entity: "order item", basePackage: "com.acme.shop", migrationVersion: "2",
  ticket: "BUG-142", summary: "Cart total ignores discount codes", component: "checkout",
  projectName: "inventory service", description: "Tracks stock levels across warehouses.",
};
const gen = { kits: [], kit: null, files: [], activeFile: 0, timer: null, selectSeq: 0, previewSeq: 0, loadingId: null };

async function loadGenerate() {
  gen.kits = await api("GET", "/api/kits");
  const list = $("#kit-picker");
  list.replaceChildren(...gen.kits.map((k) =>
    el("li", {
      class: "kit-card" + (gen.kit && gen.kit.id === k.id ? " selected" : ""),
      tabindex: "0",
      onclick: () => selectKit(k.id),
      onkeydown: (e) => { if (e.key === "Enter") selectKit(k.id); },
    },
    el("div", { class: "name" }, k.name),
    el("div", { class: "meta" },
      k.category ? el("span", { class: "badge" }, k.category) : null,
      k.fileCount + " files · saves ~" + k.minutesSaved + " min"))));
  if (!gen.kit) {
    const wanted = Number(store.get("lastKit"));
    const first = gen.kits.find((k) => k.id === wanted) || gen.kits[0];
    if (first) selectKit(first.id);
  }
}

async function selectKit(id) {
  // Already showing (or loading) this kit: do nothing, so typed values are never redrawn away.
  if ((gen.kit && gen.kit.id === id) || gen.loadingId === id) return;
  // If the user clicks another kit before this response arrives, drop this one.
  const token = ++gen.selectSeq;
  gen.loadingId = id;
  let kit;
  try {
    kit = await api("GET", "/api/kits/" + id);
  } finally {
    if (token === gen.selectSeq) gen.loadingId = null;
  }
  if (token !== gen.selectSeq) return;
  const typed = currentValues();
  gen.kit = kit;
  store.set("lastKit", String(id));
  document.querySelectorAll("#kit-picker .kit-card").forEach((c, i) =>
    c.classList.toggle("selected", gen.kits[i] && gen.kits[i].id === id));
  $("#generate-empty").hidden = true;
  $("#generate-body").hidden = false;
  $("#gen-kit-desc").textContent = gen.kit.description || "";
  $("#gen-errors").hidden = true;
  $("#gen-status").textContent = "";

  const form = $("#var-form");
  form.replaceChildren(...gen.kit.variables.map((name) => {
    const input = el("input", {
      name, required: true, maxlength: "200",
      placeholder: EXAMPLES[name] ? "e.g. " + EXAMPLES[name] : "",
      oninput: () => { store.set("var." + name, input.value); schedulePreview(); },
    });
    // Keep what the user already typed in a same-named field, else the last value they used.
    input.value = typed[name] || store.get("var." + name) || "";
    return el("label", {}, labelFor(name), input);
  }));
  if (!gen.kit.variables.length) form.append(el("p", { class: "muted" }, "This kit has no variables."));
  gen.activeFile = 0;
  refreshPreview();
}

function labelFor(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").replace(/^./, (c) => c.toUpperCase());
}

function currentValues() {
  const values = {};
  new FormData($("#var-form")).forEach((v, k) => { values[k] = String(v); });
  return values;
}

function schedulePreview() {
  clearTimeout(gen.timer);
  gen.timer = setTimeout(refreshPreview, 250);
}

async function refreshPreview() {
  const token = ++gen.previewSeq;   // only the newest preview request may update the screen
  const values = currentValues();
  const missing = gen.kit.variables.filter((v) => !(values[v] || "").trim());
  $("#gen-errors").hidden = true;
  if (missing.length) {
    $("#preview-count").textContent = "";
    $("#preview-files").replaceChildren(el("li", { class: "muted" }, "Fill in: " + missing.map(labelFor).join(", ")));
    $("#viewer-path").textContent = "";
    $("#viewer-content").textContent = "";
    return;
  }
  try {
    const result = await api("POST", "/api/kits/" + gen.kit.id + "/preview", { variables: values });
    if (token !== gen.previewSeq) return;
    gen.files = result.files;
    if (gen.activeFile >= gen.files.length) gen.activeFile = 0;
    renderPreview();
  } catch (err) {
    if (token === gen.previewSeq) showErrors($("#gen-errors"), err);
  }
}

function renderPreview() {
  $("#preview-count").textContent = "(" + gen.files.length + " files)";
  $("#preview-files").replaceChildren(...gen.files.map((f, i) =>
    el("li", { class: i === gen.activeFile ? "active" : "", title: f.path,
      onclick: () => { gen.activeFile = i; renderPreview(); } }, f.path)));
  const f = gen.files[gen.activeFile];
  $("#viewer-path").textContent = f ? f.path : "";
  $("#viewer-content").textContent = f ? f.content : "";
}

$("#btn-copy").addEventListener("click", async () => {
  const f = gen.files[gen.activeFile];
  if (!f) return;
  try {
    await navigator.clipboard.writeText(f.content);
    toast("Copied " + f.path);
  } catch (_) {
    toast("Copy failed: your browser blocked clipboard access");
  }
});

$("#btn-download").addEventListener("click", async () => {
  const btn = $("#btn-download");
  btn.disabled = true;
  $("#gen-errors").hidden = true;
  try {
    const res = await api("POST", "/api/kits/" + gen.kit.id + "/generate", { variables: currentValues() }, { raw: true });
    await downloadResponse(res, "devflow-kit.zip");
    $("#gen-status").textContent = "Downloaded. Unzip it in your project folder.";
    toast("ZIP ready: " + gen.files.length + " files, ~" + gen.kit.minutesSaved + " min saved");
  } catch (err) {
    showErrors($("#gen-errors"), err);
  } finally {
    btn.disabled = false;
  }
});

// ---------------------------------------------------------------- KITS
const editor = { id: null, files: [], session: 0 };

async function loadKitsView() {
  const kits = await api("GET", "/api/kits");
  const rows = kits.map((k) => el("tr", {},
    el("td", {}, el("strong", {}, k.name), el("br"), el("span", { class: "muted" }, k.slug)),
    el("td", {}, k.builtIn ? el("span", { class: "badge" }, "built-in") : null,
      k.category ? el("span", { class: "badge" }, k.category) : null),
    el("td", { class: "num" }, String(k.fileCount)),
    el("td", {}, el("code", {}, k.variables.join(", ") || "none")),
    el("td", {}, el("div", { class: "row-actions" },
      el("button", { class: "small", onclick: () => { gen.kit = null; store.set("lastKit", String(k.id)); showView("generate"); } }, "Use"),
      k.builtIn ? null : el("button", { class: "small", onclick: () => openEditor(k.id) }, "Edit"),
      el("button", { class: "small", onclick: () => duplicateKit(k.id) }, k.builtIn ? "Duplicate to edit" : "Duplicate"),
      el("button", { class: "small", onclick: () => exportKit(k.id) }, "Export"),
      k.builtIn ? null : el("button", { class: "small danger", onclick: () => deleteKit(k) }, "Delete")))));
  $("#kits-table").replaceChildren(el("div", { class: "table-wrap" }, el("table", { class: "data" },
    el("thead", {}, el("tr", {}, ["Kit", "Tags", "Files", "Variables", ""].map((h) => el("th", {}, h)))),
    el("tbody", {}, rows))));
}

async function duplicateKit(id) {
  try {
    const copy = await api("POST", "/api/kits/" + id + "/duplicate");
    toast("Created " + copy.name);
    await loadKitsView();
    openEditor(copy.id);
  } catch (err) { toast(err.message); }
}

async function exportKit(id) {
  try {
    const res = await api("GET", "/api/kits/" + id + "/export", undefined, { raw: true });
    await downloadResponse(res, "kit.devflow-kit.json");
  } catch (err) { toast(err.message); }
}

async function deleteKit(k) {
  if (!confirm("Delete kit \"" + k.name + "\"? History entries are kept.")) return;
  try {
    await api("DELETE", "/api/kits/" + k.id);
    if (gen.kit && gen.kit.id === k.id) gen.kit = null;
    toast("Deleted " + k.name);
    loadKitsView();
  } catch (err) { toast(err.message); }
}

$("#import-file").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file) return;
  try {
    const kit = JSON.parse(await file.text());
    const created = await api("POST", "/api/kits", kit);
    toast("Imported " + created.name);
    loadKitsView();
  } catch (err) {
    toast("Import failed: " + err.message + (err.problems && err.problems.length ? " (" + err.problems[0] + ")" : ""));
  }
});

$("#btn-new-kit").addEventListener("click", () => openEditor(null));
$("#btn-cancel-kit").addEventListener("click", () => { editor.session++; $("#kit-editor").hidden = true; });
$("#btn-add-file").addEventListener("click", () => {
  editor.files.push({ path: "", content: "" });
  renderEditorFiles();
});

async function openEditor(id) {
  editor.session++;
  editor.id = id;
  const kit = id ? await api("GET", "/api/kits/" + id) : {
    name: "", category: "", description: "", minutesSaved: 15,
    files: [{ path: "notes/{{name|kebab}}.md", content: "# {{name|title}}\n\nCreated {{today}}.\n" }],
  };
  $("#editor-title").textContent = id ? "Edit: " + kit.name : "New kit";
  $("#ed-name").value = kit.name;
  $("#ed-category").value = kit.category || "";
  $("#ed-description").value = kit.description || "";
  $("#ed-minutes").value = kit.minutesSaved;
  editor.files = kit.files.map((f) => ({ path: f.path, content: f.content }));
  $("#ed-errors").hidden = true;
  renderEditorFiles();
  $("#kit-editor").hidden = false;
  $("#kit-editor").scrollIntoView({ behavior: "smooth" });
}

function renderEditorFiles() {
  $("#ed-files").replaceChildren(...editor.files.map((f, i) => {
    const path = el("input", { placeholder: "path/to/{{name|kebab}}.txt", "aria-label": "File path",
      oninput: () => { f.path = path.value; updateDetectedVars(); } });
    path.value = f.path;
    const content = el("textarea", { class: "code", spellcheck: "false", "aria-label": "File content",
      oninput: () => { f.content = content.value; updateDetectedVars(); } });
    content.value = f.content;
    return el("div", { class: "file-editor" },
      el("div", { class: "row" }, path,
        el("button", { class: "small danger", type: "button", title: "Remove file",
          onclick: () => { editor.files.splice(i, 1); renderEditorFiles(); } }, "Remove")),
      content);
  }));
  updateDetectedVars();
}

function updateDetectedVars() {
  const re = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*((?:\|\s*[A-Za-z]+\s*)*)\}\}/g;
  const names = new Set();
  for (const f of editor.files) {
    for (const text of [f.path, f.content]) {
      for (const m of text.matchAll(re)) if (m[1] !== "today" && m[1] !== "year") names.add(m[1]);
    }
  }
  $("#ed-vars").textContent = names.size ? "Form fields: " + [...names].join(", ") : "No variables yet";
}

$("#btn-save-kit").addEventListener("click", async () => {
  const body = {
    name: $("#ed-name").value,
    category: $("#ed-category").value,
    description: $("#ed-description").value,
    minutesSaved: Number($("#ed-minutes").value) || 0,
    files: editor.files,
  };
  const btn = $("#btn-save-kit");
  const session = editor.session;   // the editor may be reopened while this save is running
  btn.disabled = true;              // prevents double-clicks from creating the kit twice
  try {
    const saved = editor.id
      ? await api("PUT", "/api/kits/" + editor.id, body)
      : await api("POST", "/api/kits", body);
    toast("Saved " + saved.name);
    gen.kit = null;
    if (session === editor.session) $("#kit-editor").hidden = true;
    await loadKitsView();
  } catch (err) {
    if (session === editor.session) showErrors($("#ed-errors"), err);
    else toast("Save failed: " + err.message);
  } finally {
    btn.disabled = false;
  }
});

// ---------------------------------------------------------------- HISTORY
async function loadHistory() {
  const [stats, runs] = await Promise.all([api("GET", "/api/stats"), api("GET", "/api/runs")]);
  const hours = (stats.totalMinutesSaved / 60).toFixed(1);
  const tile = (value, label) => el("div", { class: "stat" }, el("div", { class: "value" }, value), el("div", { class: "label" }, label));
  $("#stats").replaceChildren(
    tile(hours + " h", "estimated time saved"),
    tile(String(stats.totalRuns), "kits generated"),
    tile(String(stats.runsLast7Days), "in the last 7 days"),
    tile(String(stats.totalFiles), "files created"));
  $("#runs-table").replaceChildren(runs.length
    ? el("div", { class: "table-wrap" }, el("table", { class: "data" },
      el("thead", {}, el("tr", {}, ["When", "Kit", "Inputs", "Files", "Saved"].map((h) => el("th", {}, h)))),
      el("tbody", {}, runs.map((r) => el("tr", {},
        el("td", {}, new Date(r.createdAt).toLocaleString()),
        el("td", {}, r.kitName),
        el("td", {}, el("code", {}, r.inputs)),
        el("td", { class: "num" }, String(r.fileCount)),
        el("td", { class: "num" }, "~" + r.minutesSaved + " min"))))))
    : el("p", { class: "muted" }, "Nothing generated yet. Download a kit from the Generate tab."));
}

// ---------------------------------------------------------------- start
checkHealth();
setInterval(checkHealth, 30000);
loadGenerate().catch((err) => toast("Could not load kits: " + err.message));
