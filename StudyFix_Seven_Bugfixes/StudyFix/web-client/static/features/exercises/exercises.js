// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state } from "../../core/state.js";
import { assignment } from "../../core/api.js";
import { fetchDebugExercise } from "../room/room.js";
import { cryptoRandomId } from "../../shared/crypto.js";
import { esc, dateTime, diffClass, humanVerdict, initial, modeClass, modeLabel, statusClass } from "../../shared/format.js";
import { btn, pill, empty } from "../../ui/components.js";
import { modal } from "../../ui/modal.js";
import { pageHeader, render } from "../../ui/render.js";
import { toast } from "../../ui/toast.js";

export function renderExercises() {
  const teacher = state.identity.role === "TEACHER";
  if (!state.room)
    return `${pageHeader("▤", "Bài tập", teacher ? "Ngân hàng bài và giao bài" : "Bài được giao")}<div class="notice-box warn">Bạn cần vào một phòng học để truy cập bài tập của phòng.</div>${empty("▣", "Chưa có phòng học", "Vào hoặc tạo phòng để tiếp tục.", "Đi đến Phòng học", "navigate")}`;
  if (!teacher) return renderStudentAssignments();
  const filtered = state.catalog.filter(
    (ex) =>
      (!state.catalogDifficulty || ex.difficulty === state.catalogDifficulty) &&
      (!state.catalogQuery ||
        `${ex.id} ${ex.title}`
          .toLowerCase()
          .includes(state.catalogQuery.toLowerCase())),
  );
  return `${pageHeader("▤", "Ngân hàng bài tập", "Chọn bài trực tiếp từ catalog, xem đề và giao bài trong cùng một trang.", `<span class="chip">${state.catalog.length} bài</span><button class="btn btn-soft btn-sm" data-action="load-catalog">⟳ Làm mới ngân hàng</button>`)}
    ${state.catalogError ? `<div class="notice-box warn">${esc(state.catalogError)}. Hãy xác nhận bạn là giáo viên chủ phòng.</div>` : ""}
    <section class="card" style="margin-bottom:16px"><div class="filter-panel"><div class="form-row search-filter"><label>Tìm theo mã hoặc tên bài</label><input id="catalog-query" class="field" placeholder="Ví dụ: SEQ001, Fibonacci…" value="${esc(state.catalogQuery)}" /></div><div class="form-row"><label>Loại bài</label><select id="catalog-mode" class="select"><option value="">Tất cả</option><option value="BUILD_FROM_SCRATCH" ${state.catalogMode === "BUILD_FROM_SCRATCH" ? "selected" : ""}>Lập trình từ đầu</option><option value="DEBUG_COLLAB" ${state.catalogMode === "DEBUG_COLLAB" ? "selected" : ""}>Sửa lỗi cùng phòng</option></select></div><div class="form-row"><label>Chủ đề</label><select id="catalog-topic" class="select"><option value="">Tất cả</option>${[...new Set(state.catalog.map((e) => e.topic).filter(Boolean))].map((t) => `<option ${state.catalogTopic === t ? "selected" : ""} value="${esc(t)}">${esc(t)}</option>`).join("")}</select></div><div class="form-row"><label>Độ khó</label><select id="catalog-difficulty" class="select"><option value="">Tất cả</option>${["Dễ", "Trung bình", "Khó", "EASY", "MEDIUM", "HARD"].map((t) => `<option value="${t}" ${state.catalogDifficulty === t ? "selected" : ""}>${t}</option>`).join("")}</select></div><button class="btn btn-primary" data-action="apply-catalog-filter">⌕ Lọc</button></div></section>
    <div class="catalog-layout"><section class="card"><div class="card-head"><div><h3>Danh sách bài</h3><p>Chọn một bài để xem mô tả và đưa vào form giao bài.</p></div><span class="push"></span><span class="chip">${filtered.length} kết quả</span></div><div class="exercise-list">${filtered.length ? filtered.map((ex) => `<div class="exercise-item ${state.chosenExercise?.id === ex.id ? "selected" : ""}"><div><h4><span class="code-id mono">${esc(ex.id)}</span> · ${esc(ex.title)}</h4><p>${esc(ex.statement || "").slice(0, 130)}${(ex.statement || "").length > 130 ? "…" : ""}</p><div class="exercise-badges">${pill(modeLabel(ex.mode), modeClass(ex.mode))}${pill(ex.topic || "Chủ đề khác", "neutral")}${pill(ex.difficulty || "—", diffClass(ex.difficulty))}</div></div><div class="tool-row">${btn("Xem đề", "select-exercise", "btn-soft btn-sm", `data-exercise-id="${esc(ex.id)}"`)}${btn("Chọn", "choose-assignment", "btn-primary btn-sm", `data-exercise-id="${esc(ex.id)}"`)}</div></div>`).join("") : `<div class="empty-slim">Không tìm thấy bài phù hợp.</div>`}</div></section>
    <aside class="catalog-detail">${state.chosenExercise ? renderExerciseDetail(state.chosenExercise) : `<section class="card">${empty("⌕", "Chọn một bài tập", "Xem đề, ví dụ, ràng buộc rồi chọn để giao bài.")}</section>`}${state.chosenExercise ? renderAssignmentForm(state.chosenExercise) : ""}</aside></div>
    <section class="card" style="margin-top:16px"><div class="card-head"><div><h3>Tiến độ lớp</h3><p>Số học sinh nhận bài, đã nộp và đã AC. Chỉ giáo viên chủ phòng được xem.</p></div><span class="push"></span>${btn("Làm mới báo cáo", "load-progress", "btn-soft btn-sm")}</div><div class="card-body">${renderProgress()}</div></section>${state.work ? renderPersonalWorkspace() : ""}`;
}

export function renderExerciseDetail(ex) {
  return `<section class="card"><div class="card-head"><div><h3>Chi tiết đề</h3><p>${esc(ex.id)}</p></div><span class="push"></span>${btn("Đóng", "clear-exercise", "btn-ghost btn-sm")}</div><div class="detail-content"><h3>${esc(ex.title)}</h3><div class="exercise-badges">${pill(modeLabel(ex.mode), modeClass(ex.mode))}${pill(ex.topic || "—", "neutral")}${pill(ex.difficulty || "—", diffClass(ex.difficulty))}</div><h4>Mô tả đề bài</h4><p>${esc(ex.statement || "Chưa có mô tả.")}</p><h4>Đầu vào</h4><p>${esc(ex.inputFormat || "Theo mô tả đề bài.")}</p><h4>Đầu ra</h4><p>${esc(ex.outputFormat || "Theo mô tả đề bài.")}</p><h4>Ràng buộc</h4><p>${esc(ex.constraints || "Không có ràng buộc thêm.")}</p>${ex.publicTests?.length ? `<h4>Ví dụ công khai</h4>${ex.publicTests.map((t, i) => `<div class="test-row"><strong>Test ${i + 1}</strong><div>Input<pre>${esc(t.input)}</pre></div><div>Output<pre>${esc(t.expectedOutput)}</pre></div></div>`).join("")}` : ""}<div class="hr"></div>${ex.mode === "BUILD_FROM_SCRATCH" ? `${btn("Thử làm / chấm thử", "teacher-trial-open", "btn-soft wide", `data-exercise-id="${esc(ex.id)}"`)}` : ""}</div></section>`;
}

export function renderAssignmentForm(ex) {
  const debug = ex.mode === "DEBUG_COLLAB";
  return `<section class="card" style="margin-top:15px"><div class="card-head"><div><h3>Giao bài</h3><p>Chọn phạm vi và hạn nộp</p></div></div><div class="form-card"><div class="section-caption">Bài đang chọn</div><div class="assignment-row" style="padding-top:0"><div class="task-number">✓</div><div class="assignment-row-main"><strong>${esc(ex.id)} · ${esc(ex.title)}</strong><small>${modeLabel(ex.mode)}</small></div></div><form id="assignment-form"><div class="form-grid" style="margin-top:16px"><div class="form-row"><label>Loại bài</label><input class="field" value="${esc(modeLabel(ex.mode))}" readonly /></div><div class="form-row"><label>Phạm vi</label><select name="scope" class="select" ${debug ? "disabled" : ""}><option value="ROOM" ${state.assignmentScope === "ROOM" ? "selected" : ""}>Cả phòng</option><option value="INDIVIDUAL" ${state.assignmentScope === "INDIVIDUAL" ? "selected" : ""} ${debug ? "disabled" : ""}>Cá nhân</option></select></div>${state.assignmentScope === "INDIVIDUAL" && !debug ? `<div class="form-row full"><label>Học sinh nhận bài</label><select name="targetUsername" class="select" required><option value="">Chọn học sinh trong phòng…</option>${state.students.map((s) => `<option value="${esc(s.username)}" ${state.assignmentTarget === s.username ? "selected" : ""}>${esc(s.displayName)} (@${esc(s.username)})</option>`).join("")}</select></div>` : ""}<div class="form-row full"><label>Hạn nộp</label><select name="deadline" class="select"><option value="0">Không giới hạn thời gian</option><option value="60">Sau 1 giờ</option><option value="1440" selected>Sau 24 giờ</option><option value="4320">Sau 3 ngày</option><option value="10080">Sau 7 ngày</option><option value="43200">Sau 30 ngày</option></select><small>Hạn được tính theo thời gian server kể từ lúc giao.</small></div></div><button class="btn btn-primary wide" type="submit" style="margin-top:17px">➤ Giao bài cho ${state.assignmentScope === "INDIVIDUAL" ? "học sinh" : "cả phòng"}</button></form>${debug ? `<div class="subtle-text" style="margin-top:10px">Bài sửa lỗi chỉ giao cho cả phòng. Code mẫu được tự nạp nếu trạng thái phòng an toàn.</div>` : ""}</div></section>`;
}

export function renderProgress() {
  if (state.progressError)
    return `<div class="notice-box warn">${esc(state.progressError)}</div>`;
  if (!state.progress.length)
    return `<div class="empty-slim">Chưa có dữ liệu. Sau khi giao bài, thống kê sẽ hiện ở đây.</div>`;
  return `<div class="table-wrap"><table class="data-table"><thead><tr><th>Bài tập</th><th>Phạm vi</th><th>Đã giao</th><th>Đã nộp</th><th>Đã AC</th><th>Tỷ lệ AC</th><th></th></tr></thead><tbody>${state.progress.map((p) => `<tr><td><strong>${esc(p.title || p.exerciseId)}</strong><span class="task-sub">${esc(p.exerciseId)} · ${modeLabel(p.mode)}</span></td><td>${p.scope === "ROOM" ? "Cả phòng" : `Cá nhân · ${esc(p.targetUsername || "")}`}</td><td>${p.receivedCount}</td><td>${p.submittedCount}</td><td>${p.acceptedCount}</td><td>${p.receivedCount ? Math.round((p.acceptedCount / p.receivedCount) * 100) : 0}%</td><td>${btn("Chi tiết", "progress-detail", "btn-soft btn-sm", `data-assignment-id="${esc(p.assignmentId)}"`)}</td></tr>`).join("")}</tbody></table></div>`;
}

export function renderStudentAssignments() {
  const open = state.assignments.filter((a) => a.status === "OPEN").length;
  const filtered = state.assignments.filter(
    (a) =>
      (!state.studentTaskQuery ||
        `${a.exerciseId} ${a.exerciseTitle}`
          .toLowerCase()
          .includes(state.studentTaskQuery.toLowerCase())) &&
      (!state.studentTaskStatus ||
        String(a.status || "OPEN").toUpperCase() === state.studentTaskStatus),
  );
  return `${pageHeader("▤", "Bài được giao", "Danh sách bài của bạn. Mở đề, viết code, nộp bài và xem verdict ngay trong workspace.", `<span class="chip">${open} bài đang mở</span>`)}
    <section class="card"><div class="filter-panel"><div class="form-row search-filter"><label>Tìm bài tập</label><input id="student-task-query" class="field" placeholder="Nhập mã hoặc tên bài…" value="${esc(state.studentTaskQuery || "")}" /></div><div class="form-row"><label>Trạng thái</label><select id="student-task-status" class="select"><option value="" ${!state.studentTaskStatus ? "selected" : ""}>Tất cả</option><option value="OPEN" ${state.studentTaskStatus === "OPEN" ? "selected" : ""}>Đang mở</option><option value="CLOSED" ${state.studentTaskStatus === "CLOSED" ? "selected" : ""}>Đã đóng</option></select></div><button class="btn btn-primary" data-action="filter-student-tasks">Lọc</button></div><div class="card-body">${renderStudentAssignmentRows(filtered)}</div></section>
    ${state.work ? renderPersonalWorkspace() : ""}`;
}

export function renderStudentAssignmentRows(rows) {
  if (!rows.length)
    return empty(
      "▤",
      "Chưa có bài được giao",
      "Khi giáo viên giao bài trong phòng, bài sẽ xuất hiện ở đây.",
    );
  return `<div class="table-wrap"><table class="data-table"><thead><tr><th>Mã bài</th><th>Tên bài</th><th>Loại bài</th><th>Hạn nộp</th><th>Trạng thái</th><th></th></tr></thead><tbody>${rows.map((a) => `<tr class="${a.acceptedByMe ? "accepted-row" : ""}"><td class="mono code-id">${esc(a.exerciseId)}</td><td><strong>${esc(a.exerciseTitle || a.exerciseId)}</strong><span class="task-sub">Được giao ${dateTime(a.createdAt)}</span></td><td>${pill(modeLabel(a.mode), modeClass(a.mode))}</td><td>${a.deadlineAt ? dateTime(a.deadlineAt) : "Không giới hạn"}</td><td>${a.acceptedByMe ? pill("AC", "ac") : pill(a.status, a.status === "OPEN" ? "easy" : "neutral")}</td><td>${btn(a.acceptedByMe ? "Xem kết quả →" : "Mở đề & làm bài →", "open-assignment", a.acceptedByMe ? "btn-soft btn-sm" : "btn-primary btn-sm", `data-assignment-id="${esc(a.id)}"`)}</td></tr>`).join("")}</tbody></table></div>`;
}

export function renderPersonalWorkspace() {
  const w = state.workDetail;
  const e = w?.exercise || {};
  const teacher = state.identity.role === "TEACHER";
  if (!w) return `<div class="notice-box">Đang tải đề bài…</div>`;
  return `<div class="card" style="margin-top:17px"><div class="card-head"><div><h3>${esc(e.id || w.exerciseId)} · ${esc(e.title || w.exerciseTitle || "Bài tập")}</h3><p>${modeLabel(w.mode)} · ${w.deadlineAt ? `Hạn ${dateTime(w.deadlineAt)}` : "Không giới hạn"}</p></div><span class="push"></span>${btn("Đóng workspace", "close-work", "btn-ghost btn-sm")}</div><div class="card-body"><div class="work-layout"><section class="card statement-card"><div class="exercise-badges">${pill(modeLabel(w.mode), modeClass(w.mode))}${pill(e.topic || "—", "neutral")}${pill(e.difficulty || "—", diffClass(e.difficulty))}</div><h3>${esc(e.title || w.exerciseTitle || w.exerciseId)}</h3><p>${esc(e.statement || "")}</p><h4>Đầu vào</h4><p>${esc(e.inputFormat || "—")}</p><h4>Đầu ra</h4><p>${esc(e.outputFormat || "—")}</p><h4>Ràng buộc</h4><p>${esc(e.constraints || "—")}</p>${e.hints ? `<details><summary style="font-size:11px;font-weight:800;cursor:pointer">Gợi ý</summary><p>${esc(e.hints)}</p></details>` : ""}${(w.publicTests || []).length ? `<h4>Ví dụ công khai</h4>${w.publicTests.map((t, i) => `<div class="test-row"><strong>Test ${i + 1}</strong><div>Input<pre>${esc(t.input)}</pre></div><div>Output<pre>${esc(t.expectedOutput)}</pre></div></div>`).join("")}` : ""}</section><div class="work-editor-column"><section class="card code-card"><div class="card-head"><div><h3>Trình soạn thảo</h3><p>Java · ${esc(e.entryFile||"Main.java")}</p></div><span class="push"></span><span class="chip">${state.workBusy ? "Đang chấm" : "Có thể nộp"}</span></div><textarea id="work-code" class="code-editor" spellcheck="false">${esc(state.workCode || e.template || "")}</textarea><div class="code-actions"><small>${teacher ? "Lượt thử của giáo viên tách khỏi thành tích học sinh." : "Đọc kỹ input/output trước khi nộp."}</small>${btn("↺ Nạp code mẫu", "reset-work-code", "btn-soft btn-sm")}${btn("Nạp code từ file", "choose-work-code-file", "btn-soft btn-sm")}<input id="work-code-file" type="file" accept=".java,.txt,text/plain" hidden />${teacher ? btn("Chạy test thử", "teacher-trial-submit", "btn-primary btn-sm") : btn("Nộp bài & chấm", "submit-work", "btn-primary btn-sm")}</div></section><section class="card submission-panel" style="margin-top:13px"><div class="card-head" style="padding:0 0 12px"><div><h3>Kết quả chấm</h3><p>${teacher ? "Kết quả thử riêng của giáo viên; không tính vào AC lớp hoặc bảng xếp hạng." : "Verdict, số test đạt và lịch sử nộp hiển thị ngay tại bài."}</p></div><span class="push"></span>${btn("Làm mới", "reload-submissions", "btn-soft btn-sm")}</div>${state.workResult ? renderSubmissionResult(state.workResult) : `<div class="empty-slim">Chưa có bài nộp trong phiên làm việc này.</div>`}<div class="hr"></div><div class="section-caption">Lịch sử nộp</div>${renderSubmissions(state.workSubmissions)}</section></div></div></div></div>`;
}

export function renderSubmissionResult(s) {
  const structure=(s.structureResults||[]).length?`<section class="pb-panel"><h3>Kết quả cấu trúc OOP</h3>${s.structureResults.map(r=>`<p>${r.passed?"✓":"✗"} <strong>${esc(r.ruleId)}</strong>${r.maxPoints!=null?` · ${r.points}/${r.maxPoints} điểm`:""} · ${esc(r.message)}${r.line?` · dòng ${Number(r.line)}`:""}</p>`).join("")}</section>`:"";
  if(s.scoreHidden&&!s.testsHidden)s={...s,feedback:(s.feedback||"")+" Điểm được ẩn đến khi kỳ thi kết thúc."};
  if(s.testsHidden)return `<div class="submission-status"><strong>${humanVerdict(s.verdict||s.status)}</strong>${pill(humanVerdict(s.verdict||s.status),statusClass(s.verdict||s.status))}</div><p class="subtle-text">${esc(s.feedback||"Chi tiết test được ẩn theo chính sách đề thi.")}</p><p class="subtle-text">${s.scoreHidden?"Điểm được ẩn đến khi kỳ thi kết thúc.":`${s.score??0}/${s.maxScore??100} điểm`}</p>`;
  return `${structure}<div class="submission-status"><strong>${humanVerdict(s.verdict || s.status)}</strong>${pill(humanVerdict(s.verdict || s.status), statusClass(s.verdict || s.status))}${s.totalTests!=null?`<span class="subtle-text">${submissionTests(s)} đạt</span><div class="result-bar"><span style="width:${s.totalTests ? Math.max(0, Math.min(100, ((s.passedTests || 0) / s.totalTests) * 100)) : 0}%"></span></div>`:""}</div><p class="subtle-text">${esc(s.feedback || "")}</p>${s.testResults?.length ? `<div>${s.testResults.map((t, i) => `<div class="test-row"><strong>Test ${t.index || t.test || i + 1} ${pill(t.verdict || t.status || (t.passed===true?"ACCEPTED":t.passed===false?"WRONG_ANSWER":"—"), statusClass(t.verdict || t.status || (t.passed===true?"ACCEPTED":t.passed===false?"WRONG_ANSWER":"")))}</strong><div>Input<pre>${esc(t.input || "Ẩn")}</pre></div><div>Kết quả công khai<pre>${esc(t.actualOutput || t.output || t.message || "Chi tiết bị giới hạn an toàn")}</pre></div></div>`).join("")}</div>` : ""}`;
}

export function renderSubmissions(rows) {
  if (!rows?.length)
    return `<div class="empty-slim">Chưa có lịch sử nộp.</div>`;
  return rows
    .slice(0, 10)
    .map(
      (s) =>
        `<div class="submission-row"><div><strong>${dateTime(s.submittedAt)}</strong><small>${esc(s.id || "").slice(0, 8)}</small></div><div>${pill(humanVerdict(s.verdict || s.status), statusClass(s.verdict || s.status))}<small style="display:block;margin-top:5px">${submissionTests(s)} · ${submissionScore(s)}</small></div><div class="right">${btn("Chi tiết", "submission-detail", "btn-soft btn-sm", `data-submission-id="${esc(s.id)}"`)}</div></div>`,
    )
    .join("");
}

function submissionScore(s){return s.scoreHidden?"Điểm đang ẩn":`${s.score??0}/${s.maxScore??100} điểm`;}
function submissionTests(s){return s.testsHidden?"Chi tiết test đang ẩn":s.totalTests==null?"Test theo quy chế":`${s.passedTests??0}/${s.totalTests??0} test`;}

export async function openAssignment(assignmentId) {
  const item = state.assignments.find((a) => a.id === assignmentId);
  if (item && (item.mode === "DEBUG_COLLAB" || item.mode === "DEBUG")) {
    state.page = "room";
    state.meetingClosed=false;
    state.meetingView="";
    state.roomTab = "code";
    state.activeDebugAssignment = item;
    await fetchDebugExercise(item);
    render();
    toast("Bài sửa lỗi cùng phòng được mở trong workspace Code cộng tác.");
    return;
  }
  const data = await assignment("ASSIGN_GET", { assignmentId });
  state.work = item || data.assignment;
  state.workDetail = {
    ...data.assignment,
    publicTests: data.publicTests || [],
    teacherPreview: state.identity.role === "TEACHER",
  };
  state.workCode = data.assignment.exercise?.template || "";
  state.workCodeDirty = false;
  state.workResult = null;
  state.workSubmissions = [];
  if (state.identity.role === "TEACHER") {
    state.previewMode = true;
    await reloadSubmissions();
  } else {
    state.previewMode = false;
    try {
      const r = await assignment("SUBMISSION_LIST", { assignmentId });
      state.workSubmissions = r.submissions || [];
      if (state.workSubmissions.length) {
        state.workResult = state.workSubmissions[0];
      }
    } catch (_) {}
  }
  render();
}

export async function openTeacherTrial(exerciseId) {
  if (state.identity?.role !== "TEACHER")
    throw new Error("Chỉ giáo viên được thử bài từ ngân hàng.");
  const r = await assignment("CATALOG_GET", { exerciseId });
  const ex = { ...r.exercise, publicTests: r.publicTests || [] };
  if (ex.mode !== "BUILD_FROM_SCRATCH")
    throw new Error("Bài sửa lỗi cùng phòng được kiểm tra tại Code cộng tác.");
  state.work = {
    id: "teacher-trial-" + ex.id,
    exerciseId: ex.id,
    exerciseTitle: ex.title,
    mode: ex.mode,
    deadlineAt: 0,
    teacherTrial: true,
  };
  state.workDetail = {
    id: state.work.id,
    exerciseId: ex.id,
    exerciseTitle: ex.title,
    mode: ex.mode,
    deadlineAt: 0,
    exercise: ex,
    publicTests: ex.publicTests,
    teacherTrial: true,
  };
  state.workCode = ex.template || "";
  state.workCodeDirty = false;
  state.workResult = null;
  state.workSubmissions = [];
  state.activeSubmissionPoll = null;
  state.previewMode = true;
  await reloadSubmissions();
  render();
}

export async function submitWork() {
  if (state.identity.role === "TEACHER") return teacherTrialSubmit();
  if (!state.workDetail) throw new Error("Chưa mở đề bài.");
  if (!state.workCode.trim()) throw new Error("Code không được để trống.");
  const key = cryptoRandomId();
  const r = await assignment("SUBMIT_CREATE", {
    assignmentId: state.workDetail.id,
    sourceCode: state.workCode,
    submissionMode: "PERSONAL",
    idempotencyKey: key,
  });
  state.workResult = r.submission;
  state.workSubmissions.unshift(r.submission);
  state.activeSubmissionPoll = {
    kind: "personal",
    id: r.submission.id,
    tries: 0,
  };
  render();
  toast("Đã nộp bài. Kết quả sẽ cập nhật tự động.", "success");
}

export async function teacherTrialSubmit() {
  if (state.identity?.role !== "TEACHER" || !state.workDetail)
    throw new Error("Hãy chọn bài trong ngân hàng trước khi thử.");
  const source = document.getElementById("work-code")?.value ?? state.workCode;
  if (!source.trim()) throw new Error("Code không được để trống.");
  state.workCode = source;
  const r = await assignment("TEACHER_TRIAL_CREATE", {
    exerciseId: state.workDetail.exerciseId,
    sourceCode: source,
    idempotencyKey: cryptoRandomId(),
  });
  const trial = r.trial;
  state.workResult = trial;
  state.workSubmissions.unshift(trial);
  state.activeSubmissionPoll = { kind: "trial", id: trial.id, tries: 0 };
  render();
  toast(r.message || "Đã gửi lượt thử riêng của giáo viên.", "success");
}

export async function reloadSubmissions() {
  if (!state.workDetail) return;
  if (state.identity?.role === "TEACHER") {
    const r = await assignment("TEACHER_TRIAL_LIST", {
      exerciseId: state.workDetail.exerciseId,
    });
    state.workSubmissions = r.trials || [];
    if (state.workSubmissions.length) {
      const latest = state.workSubmissions[0];
      const details = await assignment("TEACHER_TRIAL_RESULT", {
        trialId: latest.id,
      });
      state.workResult = details.trial;
      if (["QUEUED", "RUNNING"].includes(String(latest.status).toUpperCase()))
        state.activeSubmissionPoll = { kind: "trial", id: latest.id, tries: 0 };
      else state.activeSubmissionPoll = null;
    } else {
      state.workResult = null;
      state.activeSubmissionPoll = null;
    }
  } else {
    const r = await assignment("SUBMISSION_LIST", {
      assignmentId: state.workDetail.id,
    });
    state.workSubmissions = r.submissions || [];
    if (state.workSubmissions.length) {
      state.workResult = state.workSubmissions[0];
      if (
        ["QUEUED", "RUNNING"].includes(
          String(state.workResult.status).toUpperCase(),
        )
      )
        state.activeSubmissionPoll = {
          kind: "personal",
          id: state.workResult.id,
          tries: 0,
        };
    }
  }
  render();
}

export async function openSubmission(id) {
  if (!state.workDetail) return;
  if (state.identity?.role === "TEACHER") {
    const r = await assignment("TEACHER_TRIAL_RESULT", { trialId: id });
    state.workResult = r.trial;
  } else {
    const r = await assignment("SUBMISSION_RESULT", { submissionId: id });
    state.workResult = r.submission;
  }
  render();
}

export function renderProgressDetail(p) {
  return `<div class="modal-head"><div><h3>Tiến độ · ${esc(p.title || p.exerciseId)}</h3><p>${p.acceptedCount}/${p.receivedCount} học sinh AC · Chọn tên học sinh để xem tổng quan các bài đã được giao.</p></div><span class="push"></span>${btn("Đóng", "close-modal", "btn-ghost btn-sm")}</div><div class="modal-body">${(p.entries || []).map((e) => `<div class="assignment-row"><div class="avatar">${esc(initial(e.displayName))}</div><div class="assignment-row-main"><button class="link-button" data-action="student-progress" data-user-id="${esc(e.userId)}" data-username="${esc(e.username)}"><strong>${esc(e.displayName || e.username)}</strong></button><small>@${esc(e.username)} · ${e.submitted ? "Đã nộp" : "Chưa nộp"} · ${e.accepted ? "Đã AC" : humanVerdict(e.latestVerdict)}</small></div>${pill(e.accepted ? "AC" : e.latestVerdict, statusClass(e.accepted ? "AC" : e.latestVerdict))}</div>`).join("") || '<div class="empty-slim">Chưa có dữ liệu học sinh.</div>'}</div>`;
}

export function renderStudentProgressDetail(userId, username) {
  const records = state.progress
    .map((p) => ({
      p,
      e: (p.entries || []).find(
        (e) =>
          (userId && e.userId === userId) ||
          (!userId && e.username === username),
      ),
    }))
    .filter((x) => x.e);
  const first = records[0]?.e;
  const name = first?.displayName || username || "Học sinh";
  const accepted = records.filter((x) => x.e.accepted);
  const submitted = records.filter((x) => x.e.submitted);
  return `<div class="modal-head"><div><h3>Tiến độ của ${esc(name)}</h3><p>@${esc(first?.username || username || "")} · Đã AC ${accepted.length}/${records.length} bài được giao · Đã nộp ${submitted.length}/${records.length}</p></div><span class="push"></span>${btn("Quay lại bài", "student-progress-back", "btn-ghost btn-sm")}</div><div class="modal-body"><div class="stats-grid" style="margin-bottom:14px"><div class="stat-card"><span>Bài được giao</span><strong>${records.length}</strong></div><div class="stat-card"><span>Đã nộp</span><strong>${submitted.length}</strong></div><div class="stat-card"><span>Đã AC</span><strong>${accepted.length}</strong></div></div><h4>Bài đã AC</h4>${accepted.length ? accepted.map((x) => `<div class="assignment-row"><div class="task-number">✓</div><div class="assignment-row-main"><strong>${esc(x.p.title || x.p.exerciseId)}</strong><small>${esc(x.p.exerciseId)} · ${modeLabel(x.p.mode)} · ${x.e.latestSubmittedAt ? dateTime(x.e.latestSubmittedAt) : "Đã được chấm AC"}</small></div>${pill("AC", "ac")}</div>`).join("") : '<div class="empty-slim">Học sinh chưa AC bài nào trong danh sách đã được giao.</div>'}<div class="hr"></div><h4>Tất cả bài được giao</h4>${records.length ? records.map((x) => `<div class="assignment-row"><div class="task-number">${x.e.accepted ? "✓" : "•"}</div><div class="assignment-row-main"><strong>${esc(x.p.title || x.p.exerciseId)}</strong><small>${esc(x.p.exerciseId)} · ${x.e.submitted ? "Đã nộp" : "Chưa nộp"}${x.p.deadlineAt ? ` · Hạn ${dateTime(x.p.deadlineAt)}` : ""}</small></div>${pill(x.e.accepted ? "AC" : humanVerdict(x.e.latestVerdict), statusClass(x.e.accepted ? "AC" : x.e.latestVerdict))}</div>`).join("") : '<div class="empty-slim">Không có bài giao trong báo cáo hiện tại.</div>'}</div>`;
}
