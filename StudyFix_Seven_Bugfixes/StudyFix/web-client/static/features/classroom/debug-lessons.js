import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { esc, dateTime } from "../../shared/format.js";
import { btn } from "../../ui/components.js";
import { modal } from "../../ui/modal.js";
import { toast, fail } from "../../ui/toast.js";
import { render } from "../../ui/render.js";
import { templateIcon } from "../../ui/template.js";

let session = null;
const library = { token: "", items: [], query: "", topic: "", status: "", loaded: false };
const bugLabels = { WRONG_ANSWER: "WA (sai kết quả)", COMPILE_ERROR: "CE (lỗi biên dịch)", RUNTIME_ERROR: "RE (lỗi khi chạy)", TIME_LIMIT_EXCEEDED: "TLE (quá thời gian)", MEMORY_LIMIT_EXCEEDED: "MLE (quá bộ nhớ)", OUTPUT_LIMIT_EXCEEDED: "OLE (quá đầu ra)" };
const pending = item => ["QUEUED", "RUNNING"].includes(item?.status);
const statusLabel = item => ({ READY: "Sẵn sàng giao", FAILED: "Cần chỉnh sửa", QUEUED: "Đang chờ kiểm chứng", RUNNING: "Đang kiểm chứng" })[item.status] || "Chưa kiểm chứng";
function current(s) { return Boolean(s) && session === s && s.token === state.token && state.identity?.role === "TEACHER" && (!s.roomMode || (s.roomId === state.room?.roomId && state.classroom?.isOwner)); }
function scope(controls = null, roomMode = false) { return { key: crypto.randomUUID(), token: state.token, roomId: state.room?.roomId, roomMode, controls, query: "" }; }
function shown(s) { return current(s) && document.querySelector("[data-debug-dialog]")?.dataset.debugDialog === s.key; }
function dialog(s, title, content, kind = "editor") {
  if (!current(s)) return;
  modal(`<header class="debug-modal-header"><span class="debug-heading-icon">${templateIcon(kind === "picker" ? "folder" : "code")}</span><div><h3>${esc(title)}</h3><p>${kind === "picker" ? "Chọn bài đã chuẩn bị để bắt đầu buổi học" : "Chuẩn bị bài sửa lỗi cho lớp học của bạn"}</p></div>${btn("Đóng", "close-modal", "debug-btn debug-btn-neutral")}</header><div class="debug-modal-body" data-debug-dialog="${esc(s.key)}">${content}</div>`);
  const panel = document.getElementById("active-modal")?.querySelector(".modal");
  if (panel) { panel.classList.add("debug-library-modal"); panel.dataset.debugKind = kind; }
}function key(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/gi, "d").toLowerCase(); }
function matches(item, query) { const text = key(`${item.title} ${item.sourceTitle} ${item.sourceId}`); return key(query).trim().split(/\s+/).every(word => text.includes(word)); }
export async function loadDebugLibrary() {
  if (state.identity?.role !== "TEACHER") return false;
  const token = state.token, response = await api("/api/debug-library");
  if (state.token !== token || state.identity?.role !== "TEACHER") return false;
  const changed = library.token !== token || JSON.stringify(library.items) !== JSON.stringify(response.items);
  if (library.token !== token) Object.assign(library,{query:"",topic:"",status:""});
  Object.assign(library, { token, items: response.items || [], loaded: true }); return changed;
}
export async function pollDebugLibrary() {
  if (library.token !== state.token || library.items.some(pending)) return loadDebugLibrary();
  return false;
}
function filtered(items, filters) {
  return items.filter(item => matches(item, filters.query || "") && (!filters.topic || (item.topic || "Chưa phân loại") === filters.topic) && (!filters.status || (filters.status === "PENDING" ? pending(item) : item.status === filters.status)));
}
function statusBadge(item) {
  const tone = item.status === "READY" ? "ready" : item.status === "FAILED" ? "failed" : "pending";
  return `<span class="debug-status ${tone}"><i></i>${esc(statusLabel(item))}</span>`;
}
function difficultyBadge(value) {
  if (!value) return '<span class="debug-muted">—</span>';
  const normalized = key(value), tone = ["de", "easy"].includes(normalized) ? "easy" : ["kho", "hard"].includes(normalized) ? "hard" : "medium";
  return `<span class="debug-difficulty ${tone}"><i></i>${esc(value)}</span>`;
}
function filtersHtml(items, filters, pickerMode) {
  const prefix = pickerMode ? "debug-picker" : "debug-library";
  const topics = [...new Set(items.map(item => item.topic || "Chưa phân loại"))].sort((a,b) => a.localeCompare(b,"vi"));
  return `<div class="debug-filter-bar"><label class="debug-search" for="${prefix}-query"><span class="debug-sr-only">Tìm bài lỗi</span>${templateIcon("search")}<input id="${prefix}-query" type="search" maxlength="160" value="${esc(filters.query || "")}" placeholder="Tìm theo mã hoặc tên bài lỗi…" autocomplete="off"></label><label class="debug-filter"><span class="debug-sr-only">Week / Dạng bài</span><select id="${prefix}-topic" aria-label="Week / Dạng bài"><option value="">Week / Dạng bài: Tất cả</option>${topics.map(topic => `<option ${filters.topic === topic ? "selected" : ""} value="${esc(topic)}">${esc(topic)}</option>`).join("")}</select></label><label class="debug-filter"><span class="debug-sr-only">Trạng thái</span><select id="${prefix}-status" aria-label="Trạng thái"><option value="">Tất cả trạng thái</option>${[["READY","Sẵn sàng giao"],["PENDING","Đang kiểm chứng"],["FAILED","Cần chỉnh sửa"]].map(([value,label]) => `<option value="${value}" ${filters.status === value ? "selected" : ""}>${label}</option>`).join("")}</select></label></div>`;
}
function tableHtml(items, filters, pickerMode = false) {
  const visible = filtered(items, filters);
  return `<div class="debug-results-caption" role="status" aria-live="polite"><span>${visible.length} / ${items.length} bài lỗi</span>${filters.query || filters.topic || filters.status ? btn("Xóa bộ lọc", pickerMode ? "classroom-debug-reset" : "debug-library-reset", "debug-reset") : '<span>Chỉ giáo viên được xem và quản lý</span>'}</div><div class="debug-table-wrap"><table class="debug-table"><thead><tr><th scope="col">Mã bài</th><th scope="col">Tên bài tập</th><th scope="col">Week / Dạng bài</th><th scope="col">Độ khó</th><th scope="col">Trạng thái</th><th scope="col" class="debug-actions-heading">Thao tác</th></tr></thead><tbody>${visible.map(item => `<tr><td class="debug-code-cell" title="${esc(item.sourceId)}">${esc(item.sourceId?.length > 22 ? item.sourceId.slice(0,20) + "…" : item.sourceId || "—")}</td><td class="debug-title-cell"><div><strong>${esc(item.title)}</strong><span class="debug-version">v${esc(item.version)}</span></div><small>${esc(item.sourceTitle || "Bài lỗi tự soạn")}</small></td><td><span class="debug-topic">${templateIcon("folder")}${esc(item.topic || "Chưa phân loại")}</span></td><td>${difficultyBadge(item.difficulty)}</td><td>${statusBadge(item)}</td><td><div class="debug-row-actions">${pickerMode ? btn("Chọn bài", "classroom-debug-select", "debug-btn debug-btn-primary", `data-id="${esc(item.id)}" data-version="${esc(item.version)}" ${item.status === "READY" ? "" : "disabled"}`) : ""}${btn(`${templateIcon(pending(item) ? "eye" : "edit")}<span>${pending(item) ? "Xem" : "Xem / sửa"}</span>`, "debug-library-edit", "debug-btn debug-btn-neutral", `data-id="${esc(item.id)}"`)}${!pickerMode ? btn(templateIcon("trash"), "debug-library-delete", "debug-btn debug-btn-icon", `data-id="${esc(item.id)}" data-version="${esc(item.version)}" aria-label="Xóa ${esc(item.title)}" title="Xóa bài" ${pending(item) ? "disabled" : ""}`) : ""}</div></td></tr>`).join("") || `<tr><td colspan="6"><div class="debug-empty">${templateIcon("folder")}<strong>${items.length ? "Không tìm thấy bài phù hợp" : "Chưa có bài lỗi nào"}</strong><p>${items.length ? "Thử tên bài khác hoặc xóa bộ lọc." : "Chọn Soạn bài lỗi để chuẩn bị bài trước buổi học."}</p></div></td></tr>`}</tbody></table></div>`;
}
export function renderDebugLibrary() {
  if (state.identity?.role !== "TEACHER") return '<p>Bạn không có quyền xem danh sách này.</p>';
  const items = library.token === state.token ? library.items : [];
  return `<div class="debug-library-page"><header class="debug-page-heading"><span class="debug-heading-icon">${templateIcon("code")}</span><div><h2>Danh sách bài lỗi</h2><p>Soạn một lần, chọn nhanh cho mỗi buổi học.</p></div><div class="debug-page-actions">${btn(`${templateIcon("history")}<span>Tải lại</span>`, "debug-library-refresh", "debug-btn debug-btn-neutral")}${btn(`${templateIcon("plus")}<span>Soạn bài lỗi</span>`, "debug-library-new", "debug-btn debug-btn-primary")}</div></header><div class="debug-library-summary"><span><strong>${items.length}</strong> bài đã lưu</span><span><i class="ready"></i>${items.filter(item => item.status === "READY").length} sẵn sàng giao</span><span><i class="pending"></i>${items.filter(pending).length} đang kiểm chứng</span></div><section class="debug-library-panel" aria-label="Danh sách bài lỗi"><div class="debug-panel-toolbar">${filtersHtml(items,library,false)}</div>${library.loaded ? tableHtml(items,library) : '<p class="debug-loading">Đang tải danh sách bài lỗi…</p>'}</section></div>`;
}
export function debugLibraryInput(el) {
  const field = { "debug-library-query": "query", "debug-library-topic": "topic", "debug-library-status": "status" }[el.id];
  if (field && library[field] !== el.value) { library[field] = el.value; render(); }
}function editorStatus(s) {
  const status = document.getElementById("debug-save-status");
  const dirty = !s.item || s.code !== s.item.starterCode || s.title !== s.item.title;
  if (status) {
    const item = s.item, v = item?.validation;
    status.dataset.state = dirty ? "draft" : pending(item) ? "pending" : item?.status === "READY" ? "ready" : "failed";
    status.textContent = dirty ? "Chưa lưu thay đổi. Tự sửa code chuẩn thành code có lỗi rồi lưu." : pending(item) ? "Code đã được lưu. Đang kiểm chứng code chuẩn và code lỗi…" : item?.status === "READY" ? `Đã lưu vào danh sách · Sẵn sàng giao. Code chuẩn: AC; code lỗi: ${bugLabels[v?.brokenVerdict] || "có lỗi"}.` : `Code đã lưu, cần chỉnh sửa: ${item?.message || "Lần kiểm chứng trước chưa hoàn tất. Hãy lưu lại để kiểm chứng."}`;
  }
  const busy = s.saving || pending(s.item);
  for (const id of ["debug-title", "debug-code", "debug-source"]) { const el = document.getElementById(id); if (el) el.disabled = busy || (id === "debug-source" && Boolean(s.item)); }
  const save = document.getElementById("debug-library-save"); if (save) save.disabled = busy || !s.title?.trim() || !s.code?.trim() || s.code === s.reference || (!dirty && s.item?.status === "READY");
  const apply = document.getElementById("debug-library-apply"); if (apply) apply.disabled = dirty || s.item?.status !== "READY";
}
function editor(s) {
  dialog(s, s.item ? "Sửa bài lỗi đã lưu" : "Soạn bài lỗi", `<div class="debug-editor"><div class="debug-editor-steps"><span><b>1</b>Chọn bài nguồn</span><span><b>2</b>Tự tạo lỗi</span><span><b>3</b>Lưu để giảng dạy</span></div><div class="debug-editor-fields"><label><span>Bài nguồn</span>${!s.item ? `<select id="debug-source">${s.sources.filter(e => e.debugEligible).map(e => `<option value="${esc(e.id)}" ${e.id === s.sourceId ? "selected" : ""} ${e.debugEligible ? "" : "disabled"}>${esc(e.displayCode || e.id)} · ${esc(e.title)}${e.debugEligible ? "" : ` — ${esc(e.debugReason || "Chưa khả dụng")}`} </option>`).join("")}</select>` : `<div class="debug-source-name">${templateIcon("folder")}${esc(s.sourceTitle || s.sourceId)}</div>`}</label><label for="debug-title"><span>Tên bài lỗi</span><input id="debug-title" maxlength="160" value="${esc(s.title)}" placeholder="Đặt tên để dễ tìm khi giảng dạy"></label></div>${!s.item ? `<p class="debug-source-note">${s.sources.filter(e=>e.debugEligible).length} bài có thể chọn. Chỉ hiển thị bài <strong>Program / Main.java</strong> đủ điều kiện soạn bài lỗi.</p>` : ""}<details class="debug-statement"><summary>${templateIcon("file")}Xem đề bài</summary><p>${esc(s.statement || "")}</p></details><div class="debug-code-columns"><section class="debug-code-pane"><header><span>${templateIcon("lock")}Code chuẩn</span><small>Chỉ đọc</small></header><textarea id="debug-reference" aria-label="Code chuẩn" readonly spellcheck="false">${esc(s.reference)}</textarea></section><section class="debug-code-pane is-editable"><header><span>${templateIcon("edit")}Code giao cho sinh viên</span><small>Java · Main.java</small></header><label class="debug-sr-only" for="debug-code">Code có lỗi do giáo viên soạn</label><textarea id="debug-code" spellcheck="false">${esc(s.code)}</textarea></section></div><p id="debug-save-status" class="debug-save-status" role="status" aria-live="polite"></p><footer class="debug-editor-footer"><span>${templateIcon("lock")}Code chuẩn chỉ hiển thị với giáo viên</span><div>${btn("Danh sách bài lỗi", s.roomMode ? "classroom-debug-pick" : "debug-library-list", "debug-btn debug-btn-neutral")}${s.roomMode ? btn("Đưa vào Master", "classroom-debug-use-saved", "debug-btn debug-btn-neutral", 'id="debug-library-apply" disabled') : ""}${btn(`${templateIcon("check")}<span>Lưu vào danh sách bài lỗi</span>`, "debug-library-save", "debug-btn debug-btn-primary", 'id="debug-library-save"')}</div></footer></div>`);  document.getElementById("debug-code")?.addEventListener("input", event => { s.code = event.target.value; editorStatus(s); });
  document.getElementById("debug-title")?.addEventListener("input", event => { s.title = event.target.value; editorStatus(s); });
  document.getElementById("debug-source")?.addEventListener("change", async event => {
    const id = event.target.value;
    if (s.code !== s.reference && !confirm("Đổi bài nguồn sẽ thay code chưa lưu bằng code chuẩn của bài mới?")) { event.target.value = s.sourceId; return; }
    try { await chooseSource(s, id); } catch (e) { fail(e); }
  });
  editorStatus(s);
}
async function chooseSource(s, id) {
  const request = (s.sourceRequest || 0) + 1; s.sourceRequest = request;
  dialog(s, "Đang tải code chuẩn", "<p>Đang đọc bài nguồn…</p>");
  const r = await api(`/api/debug-library/source/${encodeURIComponent(id)}`);
  if (!current(s) || s.sourceRequest !== request) return;
  Object.assign(s, { sourceId: id, sourceTitle: r.source.title, title: `${r.source.title} — Sửa lỗi`.slice(0, 160), code: r.source.referenceSolution, reference: r.source.referenceSolution, statement: r.source.statement, item: null }); editor(s);
}
async function openNew(controls = null, roomMode = false) {
  const s = scope(controls, roomMode); session = s; dialog(s, "Soạn bài lỗi", "<p>Đang tải ngân hàng bài tập…</p>");
  const response = await api("/api/debug-library/sources"); if (!current(s)) return;
  s.sources = (response.sources || []).filter(e => e.debugEligible); const first = s.sources[0];
  if (!first) { dialog(s, "Soạn bài lỗi", "<p>Chưa có bài nguồn phù hợp. Hiện chỉ hỗ trợ bài Program/Main.java có code chuẩn và testcase. Bài Method/Class chưa được hỗ trợ trong chức năng này.</p>"); return; }
  await chooseSource(s, first.id);
}
async function openItem(id, controls = null, roomMode = false) {
  const s = scope(controls, roomMode); session = s;
  const r = await api(`/api/debug-library/${encodeURIComponent(id)}`); if (!current(s)) return;
  Object.assign(s, { item: r.item, sourceId: r.item.sourceId, sourceTitle: r.item.sourceTitle, title: r.item.title, code: r.item.starterCode, reference: r.item.referenceSolution, statement: r.item.statement });
  editor(s); if (pending(s.item)) watch(s);
}
async function watch(s) {
  const id = s.item.id, version = s.item.version, nonce = (s.pollNonce || 0) + 1; s.pollNonce = nonce;
  const poll = async () => {
    if (!shown(s) || s.pollNonce !== nonce) return;
    try {
      const r = await api(`/api/debug-library/${encodeURIComponent(id)}`); if (!shown(s) || s.pollNonce !== nonce) return;
      if (r.item.version !== version) { document.getElementById("debug-save-status").textContent = "Bài đã thay đổi ở phiên khác. Hãy mở lại từ danh sách."; return; }
      s.item = r.item; editorStatus(s);
      if (!pending(s.item)) { toast(s.item.status === "READY" ? "Đã lưu bài lỗi, sẵn sàng chọn khi dạy học." : "Code đã lưu nhưng cần chỉnh sửa trước khi giao.", s.item.status === "READY" ? "success" : "info"); await loadDebugLibrary(); if (state.bankView === "debug") render(); return; }
      setTimeout(poll, 1200);
    } catch (e) { if (shown(s)) { const el = document.getElementById("debug-save-status"); if (el) el.textContent = `${e.message} Đóng và mở lại bài trong danh sách để xem trạng thái.`; } }
  }; await poll();
}
async function save(s) {
  if (s.saving || pending(s.item)) return;
  if (s.code === s.reference) throw new Error("Code vẫn là code chuẩn. Hãy tự sửa thành code có lỗi trước khi lưu.");
  s.saving = true; editorStatus(s);
  try {
    const body = { title: s.title, code: s.code, sourceId: s.sourceId, ...(s.item ? { expectedVersion: s.item.version } : {}) };
    const r = await api(`/api/debug-library${s.item ? `/${encodeURIComponent(s.item.id)}` : ""}`, { method: "POST", body });
    if (!current(s)) return;
    s.item = r.item; s.saving = false; editorStatus(s); await loadDebugLibrary(); if (state.bankView === "debug") render();
    if (pending(s.item)) await watch(s);
  } finally { s.saving = false; if (shown(s)) editorStatus(s); }
}
async function picker(controls) {
  const s = scope(controls, true); session = s;
  await loadDebugLibrary(); if (!current(s)) return; s.items = library.items;
  s.filters = {query:"",topic:"",status:""};
  dialog(s, "Chọn bài lỗi đã lưu", `<div class="debug-picker-heading"><p>Những bài đã kiểm chứng có thể đưa vào Master ngay.</p>${btn(`${templateIcon("plus")}<span>Soạn bài lỗi mới</span>`, "debug-library-new", "debug-btn debug-btn-primary")}</div>${filtersHtml(s.items,s.filters,true)}<div id="debug-library-items">${tableHtml(s.items,s.filters,true)}</div>`, "picker");
  for(const field of ["query","topic","status"])document.getElementById(`debug-picker-${field}`)?.addEventListener(field === "query" ? "input" : "change", event => {
    if(current(s)){s.filters[field]=event.target.value;document.getElementById("debug-library-items").innerHTML=tableHtml(s.items,s.filters,true);}
  });
}export function syncDebugLessonDialog() {
  if (library.token && library.token !== state.token) Object.assign(library, { token: "", items: [], query: "", topic: "", status: "", loaded: false });
  if (session && !current(session)) { if (document.querySelector("[data-debug-dialog]")?.dataset.debugDialog === session.key) document.getElementById("active-modal")?.remove(); session = null; }
}
export async function debugLibraryClick(action, data) {
  if (state.identity?.role !== "TEACHER") throw new Error("Chỉ giáo viên được chuẩn bị bài lỗi.");
  const controls = current(session) ? session.controls : null, roomMode = Boolean(current(session) && session.roomMode);
  if (action === "debug-library-new") { await openNew(controls, roomMode); return; }
  if (action === "debug-library-edit") { await openItem(data.id, controls, roomMode); return; }
  if (action === "debug-library-list") { document.getElementById("active-modal")?.remove(); await loadDebugLibrary(); render(); return; }
  if (action === "debug-library-reset") { Object.assign(library,{query:"",topic:"",status:""});render();return; }
  if (action === "debug-library-refresh") { await loadDebugLibrary(); render(); return; }
  if (action === "debug-library-delete") {
    if (!confirm("Xóa bài khỏi danh sách? Bản đã giao trong phòng học vẫn được giữ.")) return;
    await api(`/api/debug-library/${encodeURIComponent(data.id)}`, { method: "POST", body: { action: "DELETE", expectedVersion: Number(data.version) } }); await loadDebugLibrary(); render(); return;
  }
  if (!current(session)) throw new Error("Phiên đăng nhập hoặc phòng đã thay đổi. Hãy mở lại bài.");
  if (action === "debug-library-save") await save(session);
}
export async function debugLessonClick(action, data, controls) {
  if (!action.startsWith("classroom-debug-")) return false;
  if (!state.classroom?.isOwner) throw new Error("Chỉ giáo viên chủ phòng được chọn bài lỗi.");
  if (action === "classroom-debug-open") { await openNew(controls, true); return true; }
  if (action === "classroom-debug-reset") { if(current(session)){session.filters={query:"",topic:"",status:""};for(const field of ["query","topic","status"])document.getElementById(`debug-picker-${field}`).value="";document.getElementById("debug-library-items").innerHTML=tableHtml(session.items,session.filters,true);}return true; }
  if (action === "classroom-debug-pick") { await picker(controls); return true; }
  if (action === "classroom-debug-lesson") {
    const s = scope(controls, true); session = s;
    const r = await api(`/api/classroom/debug/previews/${encodeURIComponent(state.classroom.exercise.id)}`); if (!current(s)) return true;
    dialog(s, "Bài đã giao trong phòng", `<p>${esc(r.preview.title)}</p><h4>Code giao cho sinh viên</h4><pre class="debug-reference-code">${esc(r.preview.starterCode)}</pre><details><summary>Code chuẩn — chỉ giáo viên</summary><pre class="debug-reference-code">${esc(r.preview.referenceSolution)}</pre></details>`); return true;
  }
  if (action === "classroom-debug-select" || action === "classroom-debug-use-saved") {
    const s = session; if (!current(s)) throw new Error("Hãy mở lại danh sách trong phòng này.");
    if (controls.hasDirtyMaster()) throw new Error("Master có nháp chưa lưu. Lưu hoặc dùng bản server trước khi đổi bài.");
    if (state.classroom.phase !== "LECTURE") throw new Error("Chọn bài trong giai đoạn giảng bài.");
    const item = action === "classroom-debug-use-saved" ? s.item : { id: data.id, version: Number(data.version) };
    if (action === "classroom-debug-use-saved" && (item?.status !== "READY" || s.code !== item.starterCode || s.title !== item.title)) throw new Error("Hãy lưu và kiểm chứng các thay đổi trước khi giao.");
    await controls.sendAction("SELECT_LIBRARY", { libraryId: item.id, libraryVersion: item.version });
    document.getElementById("active-modal")?.remove(); toast("Đã chọn bài lỗi vào Master. Bấm Bắt đầu sửa lỗi để giao nhánh cho lớp.", "success"); return true;
  }
  return true;
}