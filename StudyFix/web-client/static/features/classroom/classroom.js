import { state } from "../../core/state.js";
import { api, assignment } from "../../core/api.js";
import { esc, dateTime } from "../../shared/format.js";
import { btn } from "../../ui/components.js";
import { modal } from "../../ui/modal.js";
import { render } from "../../ui/render.js";
import { toast, fail } from "../../ui/toast.js";
import { renderMeeting } from "./meeting-view.js";

const phases = { WAITING: "Chờ bắt đầu", LECTURE: "Giảng bài", BRANCHING: "Học sinh sửa nhánh", MERGING: "Chọn lời giải", ENDED: "Đã kết thúc" };
let saveTimer;
let lastAutoCheck = 0;
let generation = 0;
let mutating = 0;
let autoTimer;

function draftKey(kind, round) {
  return `studyfix.classroom.${state.identity?.userId}.${state.room?.roomId}.${kind}.${round}`;
}
function draft(kind, remote) {
  state.classroomDrafts ||= {};
  const key = draftKey(kind, kind === "master" ? 0 : state.classroom.round);
  if (!state.classroomDrafts[key]) {
    let stored;
    try { stored = JSON.parse(sessionStorage.getItem(key) || "null"); } catch (_) { /* Ignore invalid local drafts. */ }
    state.classroomDrafts[key] = stored || { code: remote.code, baseVersion: remote.version, dirty: false };
  }
  const d = state.classroomDrafts[key];
  if (!d.dirty && !d.saving) { d.code = remote.code; d.baseVersion = remote.version; }
  return d;
}
function persistDraft(kind, d) {
  const key = draftKey(kind, kind === "master" ? 0 : state.classroom.round);
  try {
    if (d.dirty) sessionStorage.setItem(key, JSON.stringify({ code: d.code, baseVersion: d.baseVersion, dirty: true }));
    else sessionStorage.removeItem(key);
  } catch (_) { toast("Bộ nhớ trình duyệt đầy. Hãy tải nháp về máy để giữ bài.", "error"); }
}
function myBranch() { return state.classroom?.branches?.find(b => b.userId === state.identity?.userId); }
function verdict(c) {
  if (!c?.id) return "Chưa kiểm tra";
  if (c.status === "QUEUED") return "Đang chờ chấm";
  if (c.status === "RUNNING") return "Đang chấm";
  return c.result?.verdict || c.status;
}
function resultPanel(c) {
  if (!c?.id) return "";
  const r = c.result || {};
  return `<div class="notice-box ${r.verdict === "ACCEPTED" ? "" : "warn"}"><strong>${esc(verdict(c))}</strong><p>${esc(r.feedback || "Kết quả sẽ cập nhật khi dịch vụ chấm hoàn tất.")}</p>${r.score !== undefined ? `<small>${esc(r.score)}/${esc(r.maxScore)} điểm · ${esc(r.passed)}/${esc(r.total)} testcase · ${esc(r.durationMs)} ms</small>` : ""}</div>`;
}
function historyChecks(checks) {
  return checks?.length ? `<div class="history-list">${checks.map(c => `<div class="history-entry"><strong>v${c.branchVersion}</strong><div>${esc(verdict(c))}<small>${dateTime(c.createdAt)}</small></div>${btn("Xem bản nộp", "classroom-check-detail", "btn-soft btn-sm", `data-id="${esc(c.id)}"`)}</div>`).join("")}</div>` : `<p class="empty-slim">Chưa có lượt kiểm tra.</p>`;
}
function dialog(title, content) {
  modal(`<div class="card-head"><h3>${esc(title)}</h3><span class="push"></span>${btn("Đóng", "close-modal", "btn-soft btn-sm")}</div><div class="card-body">${content}</div>`);
}
function localRecovery() {
  const prefix = `studyfix.classroom.${state.identity.userId}.${state.room.roomId}.branch.`;
  const old = [];
  for (let i = 0; i < sessionStorage.length; i++) {
    const key = sessionStorage.key(i);
    if (!key?.startsWith(prefix)) continue;
    const round = Number(key.slice(prefix.length));
    if (round === state.classroom.round) continue;
    try { if (JSON.parse(sessionStorage.getItem(key))?.dirty) old.push({ key, round }); } catch (_) { /* Invalid local data is ignored. */ }
  }
  return old.length ? `<section class="notice-box warn"><strong>Nháp chưa lưu của vòng trước vẫn còn trong trình duyệt</strong><div class="tool-row">${old.map(x => btn(`Lấy nháp vòng ${x.round}`, "classroom-local-recovery", "btn-soft btn-sm", `data-key="${esc(x.key)}"`)).join("")}</div></section>` : "";
}
export async function loadClassroom() {
  if (!state.room || !state.identity) { state.classroom = null; return; }
  const roomId = state.room.roomId, token = state.token, started = generation;
  const response = await api("/api/classroom");
  if (state.room?.roomId !== roomId || state.token !== token || mutating || generation !== started) return;
  const changed = JSON.stringify(state.classroom) !== JSON.stringify(response.classroom);
  if(state.classroomRoomId!==roomId) {state.meetingView="";state.meetingPanel="";state.meetingQuery="";state.meetingFilter="";state.meetingGridPage=0;state.callRoster=[];}
  state.classroom = response.classroom;
  state.classroomRoomId = roomId;
  state.classroomError = "";
  scheduleAutoCheck();
  if (changed && state.page === "room") render();
}
function scheduleAutoCheck() {
  const b = myBranch(), c = state.classroom;
  if (autoTimer || !b || c.phase !== "BRANCHING" || state.classroomAutoCheck === false || draft("branch", b).dirty ||
      ["QUEUED", "RUNNING"].includes(b.latestCheck?.status) || (b.latestCheck?.id && !b.resultStale)) return;
  const key = draftKey("branch", c.round);
  autoTimer = setTimeout(async () => {
    autoTimer = null;
    if (!state.identity || !state.room || state.classroom?.phase !== "BRANCHING" || key !== draftKey("branch", state.classroom.round) || state.classroomAutoCheck === false) return;
    const current = myBranch();
    if (!current || draft("branch", current).dirty || ["QUEUED", "RUNNING"].includes(current.latestCheck?.status) || (current.latestCheck?.id && !current.resultStale)) return;
    try { await runCheck(); } catch(e) { fail(e); }
  }, Math.max(1500, 5000 - (Date.now() - lastAutoCheck)));
}

export function renderClassroom(chat, members) {
  const c = state.classroom;
  if (!c || state.classroomRoomId !== state.room.roomId)
    return `<section class="card"><div class="card-body"><p>${esc(state.classroomError || "Đang đồng bộ phiên học…")}</p>${btn("Thử đồng bộ lại", "classroom-refresh", "btn-primary")}</div></section>`;
  const owner = c.isOwner;
  const active = !["WAITING", "ENDED"].includes(c.phase);
  const control = owner ? `<div class="tool-row">
    ${c.phase === "WAITING" ? btn("Bắt đầu giảng bài", "classroom-action", "btn-primary", 'data-op="START"') : ""}
    ${c.phase === "LECTURE" ? `${btn("Chọn code lỗi từ ngân hàng", "classroom-pick-exercise", "btn-soft")}${btn("Tách nhánh cho cả lớp", "classroom-action", "btn-primary", 'data-op="FORK"')}` : ""}
    ${c.phase === "BRANCHING" ? btn("Dừng sửa và chọn lời giải", "classroom-action", "btn-primary", 'data-op="FREEZE"') : ""}
    ${c.phase === "MERGING" ? btn("Cho học sinh sửa tiếp", "classroom-action", "btn-soft", 'data-op="RESUME"') : ""}
    ${active ? btn("Kết thúc phiên", "classroom-action", "btn-danger", 'data-op="END"') : ""}
    ${c.phase === "ENDED" ? btn("Tạo phiên học tiếp theo", "classroom-action", "btn-primary", 'data-op="NEW_SESSION"') : ""}
  </div>` : "";
  const b = myBranch();
  const masterRemote = { code: state.room.code, version: state.room.version };
  const md = owner ? draft("master", masterRemote) : masterRemote;
  const history = `<div class="meeting-history-content">${c.round ? `<label for="classroom-history-round">Vòng sửa lỗi</label><select id="classroom-history-round" class="field">${Array.from({length:c.round},(_,i)=>`<option value="${i+1}" ${i+1===Number(state.meetingHistoryRound||c.round)?"selected":""}>Vòng ${i+1}</option>`).join("")}</select>${btn("Xem bài và kết quả", "classroom-round-report", "btn-soft btn-sm")}` : ""}${b?`<h4>Lịch sử kiểm tra của bạn</h4>${historyChecks(b.checks)}`:""}<h4>Lịch sử Master</h4>${(state.room.history||[]).slice().reverse().map(h=>`<div class="history-entry"><strong>v${h.version}</strong><div>${esc(h.editorName)}<small>${dateTime(h.createdAt)}</small></div>${btn("Xem", "history-code", "btn-soft btn-sm", `data-version="${h.version}"`)}</div>`).join("")}${owner?`<h4>Nhật ký phiên học</h4>${(c.events||[]).map(e=>`<div class="history-entry"><strong>Vòng ${e.round}</strong><div>${esc(e.detail)}<small>${dateTime(e.createdAt)}</small></div></div>`).join("")}`:""}</div>`;
  return renderMeeting({c,md,b,branchDraft:b?draft("branch",b):null,control,chat,members,history,recovery:localRecovery()});
}
async function sendAction(action, data = {}) {
  const c = state.classroom;
  const roomId = state.room.roomId, token = state.token;
  generation++;mutating++;
  try {
    const response = await api("/api/classroom", { method: "POST", body: { action, expectedPhase: c.phase, round: c.round, expectedVersion: state.room.version, ...data } });
    if (state.token !== token || state.room?.roomId !== roomId) throw new Error("Bạn đã rời phiên làm việc này. Nháp được giữ trong trình duyệt.");
    state.classroom = response.classroom;
    const r = await api("/api/rooms/current");
    if (state.token === token && state.room?.roomId === roomId) state.room = r.room;
    render();return response.classroom;
  } finally {mutating--;generation++;}
}
async function save(kind) {
  const remote = kind === "master" ? { code: state.room.code, version: state.room.version } : myBranch();
  if (!remote) throw new Error("Nhánh chưa khả dụng.");
  const d = draft(kind, remote);
  if (d.saving) { await d.pendingSave; return save(kind); }
  if (!d.dirty) return true;
  const code = d.code, base = d.baseVersion;
  d.saving = true;
  d.pendingSave = (async () => { try {
    await sendAction(kind === "master" ? "MASTER_SAVE" : "BRANCH_SAVE", { code, expectedVersion: base });
    const saved = kind === "master" ? { version: state.room.version } : myBranch();
    d.baseVersion = saved.version;d.dirty = d.code !== code;
    persistDraft(kind, d);
    return !d.dirty;
  } finally {d.saving = false;render();} })();
  return d.pendingSave;
}
async function runCheck() {
  if (!(await save("branch"))) return;
  await sendAction("BRANCH_CHECK", { expectedVersion: myBranch().version });
  lastAutoCheck = Date.now();
}
export function classroomInput(el) {
  if (el.id === "meeting-query") {state.meetingQuery=el.value;state.meetingGridPage=0;render();return;}
  if (!["classroom-master", "classroom-branch"].includes(el.id)) return;
  const kind = el.id === "classroom-master" ? "master" : "branch";
  const remote = kind === "master" ? { code: state.room.code, version: state.room.version } : myBranch();
  if (!remote) return;
  const d = draft(kind, remote);d.code = el.value;d.dirty = true;persistDraft(kind, d);
  if(el.previousElementSibling)el.previousElementSibling.textContent=Array.from({length:el.value.split("\n").length},(_,i)=>i+1).join("\n");
  const status = document.getElementById("classroom-save-status");if (status) status.textContent = "Có thay đổi, đang chờ lưu…";
  if (kind === "branch") {
    clearTimeout(saveTimer);
    const scope = draftKey("branch", state.classroom.round);
    saveTimer = setTimeout(async () => {
      if (!state.identity || !state.room || !state.classroom || scope !== draftKey("branch", state.classroom.round) || state.classroom.phase !== "BRANCHING") return;
      try {
        const saved = await save("branch");
        if (saved && state.classroomAutoCheck !== false && Date.now() - lastAutoCheck > 5000 && !["QUEUED", "RUNNING"].includes(myBranch()?.latestCheck?.status)) await runCheck();
      } catch (e) { fail(e); }
    }, 1200);
  }
}
export async function classroomClick(action, d) {
  switch (action) {
    case "classroom-view": state.meetingView=d.view;state.roomTab="code";render();break;
    case "classroom-panel": state.meetingPanel=state.meetingPanel===d.panel?"":d.panel;render();break;
    case "classroom-filter": state.meetingFilter=d.filter;state.meetingGridPage=0;render();break;
    case "classroom-grid-page": state.meetingGridPage=Number(d.page);render();break;
    case "classroom-fullscreen":
      if(document.fullscreenElement)await document.exitFullscreen();
      else if(document.documentElement.requestFullscreen)await document.documentElement.requestFullscreen();
      else throw new Error("Trình duyệt không hỗ trợ chế độ toàn màn hình.");
      break;
    case "classroom-statement": {
      const e=state.classroom.exercise||{};
      dialog(e.title||"Đề bài",`<p>${esc(e.statement)}</p><h4>Input</h4><pre>${esc(e.inputFormat)}</pre><h4>Output</h4><pre>${esc(e.outputFormat)}</pre><p>${esc(e.constraints)}</p>`);break;
    }
    case "classroom-refresh": await loadClassroom();break;
    case "classroom-action":
      if (d.op === "END" && !confirm("Kết thúc phiên học? Nháp chưa lưu của học sinh sẽ không được ghi lên server sau khi kết thúc.")) return;
      if (d.op === "FORK" && draft("master", {code: state.room.code, version: state.room.version}).dirty) throw new Error("Hãy lưu Master trước khi tách nhánh.");
      await sendAction(d.op);state.meetingView=["FORK","FREEZE","RESUME"].includes(d.op)?"grid":"master";render();break;
    case "classroom-save-master": await save("master");toast("Đã lưu Master.", "success");break;
    case "classroom-save-branch": await save("branch");break;
    case "classroom-run-check": await runCheck();break;
    case "classroom-pick-exercise": {
      const r = await assignment("CATALOG_LIST", { mode: "DEBUG_COLLAB" });
      dialog("Chọn bài sửa lỗi", `<div class="history-list">${(r.exercises || []).map(e => `<div class="history-entry"><strong>${esc(e.id)}</strong><div>${esc(e.title)}</div>${btn("Dùng starter", "classroom-select-exercise", "btn-primary btn-sm", `data-id="${esc(e.id)}"`)}</div>`).join("")}</div>`);break;
    }
    case "classroom-select-exercise":
      if (draft("master", {code:state.room.code, version:state.room.version}).dirty) throw new Error("Master có nháp chưa lưu. Lưu hoặc chọn dùng bản server trước khi đổi bài.");
      if (!confirm("Nạp starter của bài này vào Master? Phiên bản Master hiện tại được giữ trong lịch sử.")) return;
      await sendAction("SELECT_EXERCISE", { exerciseId: d.id });state.meetingView="master";render();document.getElementById("active-modal")?.remove();break;
    case "classroom-preview": {
      const r = await api(`/api/classroom/branches/${encodeURIComponent(d.userId)}?round=${Number(d.round || state.classroom.round)}`), b = r.branch;
      dialog(`Bài của ${b.displayName} · Vòng ${b.round} · v${b.version}`, `<textarea class="code-editor" readonly aria-label="Code nhánh học sinh">${esc(b.code)}</textarea>${historyChecks(b.checks)}${state.classroom.phase === "MERGING" && b.round === state.classroom.round ? `<div class="tool-row">${btn("Chọn nhánh này làm Master", "classroom-merge", "btn-primary", `data-user-id="${esc(b.userId)}" data-version="${b.version}" data-round="${b.round}" data-master-version="${state.room.version}"`)}</div>` : ""}`);break;
    }
    case "classroom-merge":
      if (!confirm("Dùng đúng phiên bản nhánh đang xem làm Master chung cho cả lớp?")) return;
      await sendAction("MERGE", {userId: d.userId, branchVersion: Number(d.version), round: Number(d.round), expectedVersion: Number(d.masterVersion)});
      state.meetingView="master";render();
      document.getElementById("active-modal")?.remove();toast("Đã Merge lời giải vào Master.", "success");break;
    case "classroom-check-detail": {
      const r = await api(`/api/classroom/checks/${encodeURIComponent(d.id)}`);
      dialog(`Code đã kiểm tra · v${r.check.branchVersion}`, `${resultPanel(r.check)}<textarea class="code-editor" readonly aria-label="Snapshot code đã nộp">${esc(r.check.code)}</textarea>`);break;
    }
    case "classroom-round-report": {
      const round = Number(document.getElementById("classroom-history-round")?.value || state.classroom.round);
      const r = await api(`/api/classroom/rounds/${round}`);
      dialog(`Bài làm vòng ${round}`, `<div class="history-list">${r.report.branches.map(b => `<div class="history-entry"><strong>${esc(b.displayName)}</strong><div>v${b.version} · ${esc(verdict(b.latestCheck))}${b.resultStale ? " · kết quả bản cũ" : ""}</div>${btn("Xem bài", "classroom-preview", "btn-soft btn-sm", `data-user-id="${esc(b.userId)}" data-round="${round}"`)}</div>`).join("") || '<p>Không có bài làm được phép xem trong vòng này.</p>'}</div>`);break;
    }
    case "classroom-local-recovery": {
      const prefix = `studyfix.classroom.${state.identity.userId}.${state.room.roomId}.branch.`;
      if (!d.key?.startsWith(prefix)) throw new Error("Nháp không thuộc phiên đăng nhập này.");
      const old = JSON.parse(sessionStorage.getItem(d.key) || "null");
      if (!old) throw new Error("Không tìm thấy nháp.");
      dialog("Nháp chưa lưu của vòng trước", `<p>Bạn có thể sao chép code này để giữ bài. Đây là nháp trên trình duyệt, chưa được xác nhận đã lưu trên server.</p><textarea class="code-editor" readonly aria-label="Nháp khôi phục">${esc(old.code)}</textarea>`);break;
    }
    case "classroom-download": {
      const remote = d.kind === "master" ? {code: state.room.code, version: state.room.version} : myBranch();
      const url = URL.createObjectURL(new Blob([draft(d.kind, remote).code], {type: "text/plain;charset=utf-8"}));
      const a = document.createElement("a");a.href = url;a.download = "Main.java";a.click();setTimeout(() => URL.revokeObjectURL(url), 1000);break;
    }
    case "classroom-reload-draft": {
      if (!confirm("Dùng code trên server thay nháp đang hiển thị? Hãy tải nháp về trước nếu cần giữ.")) return;
      const remote = d.kind === "master" ? {code: state.room.code, version: state.room.version} : myBranch();
      const local = draft(d.kind, remote);local.code = remote.code;local.baseVersion = remote.version;local.dirty = false;persistDraft(d.kind, local);render();break;
    }
    case "classroom-release-legacy":
      await api("/api/rooms/current/command", {method: "POST", body: {action: "CANCEL_DRAFT", token: state.room.token}});break;
  }
}
