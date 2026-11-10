// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state } from "../../core/state.js";
import { assignment } from "../../core/api.js";
import { renderSubmissionResult } from "../exercises/exercises.js";
import { cryptoRandomId } from "../../shared/crypto.js";
import { esc, dateTime, diffClass, nowMs } from "../../shared/format.js";
import { btn, pill, empty } from "../../ui/components.js";
import { modal } from "../../ui/modal.js";
import { pageHeader, render } from "../../ui/render.js";
import { toast } from "../../ui/toast.js";
import {loadExam,renderExamMonitor,renderStudentExamStatus} from "./exam-monitoring.js";

export function renderContests() {
  if (!state.room)
    return `${pageHeader("♜", "Thi & xếp hạng", "Kỳ thi được quản lý theo phòng học.")}${empty("♜", "Chưa có phòng học", "Vào phòng học để xem hoặc tạo kỳ thi.", "Đi đến Phòng học", "navigate")}`;
  const teacher = state.identity.role === "TEACHER";
  return `${pageHeader("♜", teacher ? "Thi & xếp hạng" : "Kỳ thi", teacher ? "Tạo kỳ thi trực tiếp từ ngân hàng và xem bảng xếp hạng của lớp." : "Xem kỳ thi được mở, làm bài và theo dõi kết quả của chính mình.", teacher ? btn("＋ Tạo kỳ thi", "open-contest-create", "btn-primary") : "")}
    ${state.contestWork && !teacher ? renderContestWorkspace() : ""}
    ${state.contestFormOpen && teacher ? renderContestCreator() : ""}
    ${state.selectedContest ? renderContestDetail() : ""}
    <div class="grid ${teacher ? "contest-grid" : "contest-grid-student"}"><section class="card"><div class="card-head"><div><h3>${teacher ? "Kỳ thi của phòng" : "Kỳ thi có thể tham gia"}</h3><p>Thời gian được tính theo đồng hồ của server.</p></div><span class="push"></span>${btn("⟳", "load-contests", "btn-soft btn-sm")}</div><div class="card-body"><div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">${state.contests.length ? state.contests.map((c) => renderContestCard(c, teacher)).join("") : `<div class="empty" style="grid-column:1/-1"><div class="empty-icon">♜</div><strong>Chưa có kỳ thi</strong><div>${teacher ? "Tạo kỳ thi và chọn các bài từ ngân hàng." : "Khi giáo viên mở kỳ thi, bạn sẽ thấy tại đây."}</div>${teacher ? `<div style="margin-top:14px">${btn("Tạo kỳ thi đầu tiên", "open-contest-create", "btn-primary")}</div>` : ""}</div>`}</div></div></section>
    ${teacher ? `<aside class="card"><div class="card-head"><div><h3>Bảng xếp hạng</h3><p>Sắp xếp theo số bài AC giảm dần</p></div></div><div class="card-body">${state.leaderboard.length ? renderLeaderboard(state.leaderboard) : `<div class="empty-slim">Chọn một kỳ thi để xem bảng xếp hạng.</div>`}</div></aside>` : ""}</div>`;
}

export function contestIsLive(c) {
  const now = Date.now();
  return now >= Number(c.startAt || 0) && now < Number(c.endAt || 0);
}

export function renderContestCard(c, teacher) {
  const live = contestIsLive(c);
  const start = Number(c.startAt || 0);
  const end = Number(c.endAt || 0);
  const past = nowMs() > end;
  return `<div class="card contest-card"><div class="contest-card-head"><div class="contest-logo">♜</div><div><h3>${esc(c.title || c.name || "Kỳ thi StudyFix")}</h3><p>${esc(!teacher?(c.assignedProblemCount??(c.distributionMode==="RANDOM"?c.problemsPerStudent:c.problemCount??c.exerciseCount??"")):(c.problemCount??c.exerciseCount??""))} bài${c.distributionMode==="RANDOM"?" · Phát ngẫu nhiên":""} · ${c.contestType === "INDIVIDUAL" ? "Cá nhân" : "Nhóm"}</p></div><span class="push"></span>${pill(past ? "Đã kết thúc" : live ? "Đang diễn ra" : "Sắp diễn ra", past ? "neutral" : live ? "easy" : "medium")}</div><div class="contest-meta"><span class="chip">◷ ${dateTime(start)}</span><span class="chip">Kết thúc ${dateTime(end)}</span></div><div class="footer-row">${btn(teacher ? "Xem kỳ thi" : "Vào thi →", "select-contest", "btn-primary", `data-contest-id="${esc(c.id || c.contestId)}"`)}${teacher ? btn("Giao đề", "contest-assign-open", "btn-soft btn-sm", `data-contest-id="${esc(c.id||c.contestId)}" ${past?"disabled":""}`) : ""}${teacher ? btn("BXH", "open-leaderboard", "btn-soft btn-sm", `data-contest-id="${esc(c.id || c.contestId)}"`) : ""}</div></div>`;
}

export function renderContestWorkspace() {
  const w = state.contestWork;
  if (!w) return "";
  const e = w.problem || {};
  const c = state.contestDetail?.contest || {};
  return `<section class="card" style="margin-bottom:16px"><div class="card-head"><div><h3>${esc(e.id)} · ${esc(e.title)}</h3><p>${esc(c.title || "Kỳ thi")} · ${contestIsLive(c) ? "Đang diễn ra" : "Chỉ xem đề"}</p></div><span class="push"></span>${btn("Đóng bài thi", "close-contest-work", "btn-ghost btn-sm")}</div><div class="card-body"><div class="work-layout"><section class="card statement-card"><h3>${esc(e.title)}</h3><p>${esc(e.statement || "")}</p><h4>Đầu vào</h4><p>${esc(e.inputFormat || "—")}</p><h4>Đầu ra</h4><p>${esc(e.outputFormat || "—")}</p><h4>Ràng buộc</h4><p>${esc(e.constraints || "—")}</p>${(e.publicTests || []).map((t, i) => `<div class="test-row"><strong>Ví dụ ${i + 1}</strong><div>Input<pre>${esc(t.input)}</pre></div><div>Output<pre>${esc(t.expectedOutput)}</pre></div></div>`).join("")}</section><div class="work-editor-column"><section class="card code-card"><div class="card-head"><div><h3>Trình soạn thảo bài thi</h3><p>Main.java</p></div><span class="push"></span>${pill(contestIsLive(c) ? "ĐANG THI" : "CHƯA THỂ NỘP", contestIsLive(c) ? "easy" : "neutral")}</div>${renderStudentExamStatus()}<textarea id="contest-code" class="code-editor" spellcheck="false">${esc(state.contestCode || e.template || "")}</textarea><div class="code-actions"><small>Đồng hồ và thời hạn do server xác minh.</small>${btn("Nạp code mẫu", "reset-contest-code", "btn-soft btn-sm")}<label class="btn btn-soft btn-sm"><input id="contest-code-file" type="file" accept=".java,.txt" hidden ${contestIsLive(c)?"":"disabled"}/>Nộp file code</label>${btn("Nộp bài thi", "submit-contest-work", "btn-primary btn-sm", contestIsLive(c) ? "" : "disabled")}</div></section><section class="card submission-panel" style="margin-top:13px"><div class="section-caption">Kết quả nộp</div>${w.result ? renderSubmissionResult(w.result) : '<div class="empty-slim">Chưa nộp bài này.</div>'}</section></div></div></div></section>`;
}

export function renderContestCreator() {
  const filtered = state.catalog.filter(
    (e) =>
      e.mode === "BUILD_FROM_SCRATCH" &&
      (!state.contestQuery ||
        `${e.id} ${e.title} ${e.topic}`
          .toLowerCase()
          .includes(state.contestQuery.toLowerCase())),
  );
  return `<section class="card" style="margin-bottom:16px"><div class="card-head"><div><h3>Tạo kỳ thi từ ngân hàng bài</h3><p>Chọn bài bằng mã bài từ catalog; không yêu cầu giao assignment trước.</p></div><span class="push"></span>${btn("Đóng", "close-contest-create", "btn-ghost btn-sm")}</div><div class="contest-create-body"><form id="contest-form"><div class="form-grid"><div class="form-row full"><label>Tên kỳ thi</label><input class="field" name="title" required maxlength="120" placeholder="Ví dụ: Kiểm tra cấu trúc dữ liệu tuần 3" value="${esc(state.contestTitle)}" /></div><div class="form-row"><label>Bắt đầu sau</label><select name="startInMinutes" class="select">${[[0, "Bắt đầu ngay"], [5, "Sau 5 phút"], [15, "Sau 15 phút"], [60, "Sau 1 giờ"], [1440, "Ngày mai"]].map(([v, label]) => `<option value="${v}" ${Number(state.contestStart) === v ? "selected" : ""}>${label}</option>`).join("")}</select></div><div class="form-row"><label>Thời lượng</label><select name="durationMinutes" class="select">${[
    [30, "30 phút"],
    [45, "45 phút"],
    [60, "1 giờ"],
    [90, "1 giờ 30 phút"],
    [120, "2 giờ"],
    [180, "3 giờ"],
    [1440, "24 giờ"],
  ]
    .map(
      ([v, t]) =>
        `<option value="${v}" ${Number(state.contestDuration) === v ? "selected" : ""}>${t}</option>`,
    )
    .join(
      "",
    )}</select></div><div class="form-row"><label>Phát đề</label><select class="select" name="distributionMode"><option value="ALL" ${state.contestDistribution!=="RANDOM"?"selected":""}>Cùng đề cho cả lớp</option><option value="RANDOM" ${state.contestDistribution==="RANDOM"?"selected":""}>Ngẫu nhiên cho từng sinh viên</option></select></div><div class="form-row"><label>Số bài mỗi sinh viên</label><input class="field" type="number" name="problemsPerStudent" min="1" max="${Math.max(1,state.contestSelected.length)}" value="${esc(state.contestRandomCount||1)}" ${state.contestDistribution==="RANDOM"?"required":"disabled"}/><small>Chọn nhóm bài bên dưới. Đề bốc một lần, tải lại không đổi đề; các sinh viên có thể trùng bài.</small></div></div><div class="hr"></div><div class="form-row"><label>Tìm bài có thể thi</label><input class="field" id="contest-query" placeholder="Mã bài, tên bài, chủ đề…" value="${esc(state.contestQuery)}" /><small>Contest cá nhân chỉ chọn BUILD_FROM_SCRATCH. Bài sửa lỗi cộng tác được giữ trong workflow phòng học, không bị tính nhầm là bài cá nhân.</small></div><div class="exam-settings" style="margin:12px 0"><label>Số bài chọn ngẫu nhiên<input class="field" id="contest-pool-draw-count" type="number" min="1" max="${Math.min(20,filtered.length)}" value="${state.contestPoolDrawCount||1}"/></label>${btn("Chọn ngẫu nhiên từ danh sách","contest-pick-random","btn-soft")}</div><div class="contest-problem-list">${filtered.map((e) => `<label class="check-row"><input type="checkbox" data-contest-exercise="${esc(e.id)}" ${state.contestSelected.includes(e.id) ? "checked" : ""} /><span style="flex:1"><strong>${esc(e.id)} · ${esc(e.title)}</strong><small>${esc(e.topic || "")} · ${esc(e.difficulty || "")}</small></span>${pill(e.difficulty || "—", diffClass(e.difficulty))}</label>`).join("") || `<div class="empty-slim">Không có bài lập trình từ đầu trong bộ lọc này.</div>`}</div><div class="footer-row"><span class="subtle-text">Đã chọn ${state.contestSelected.length}/20 bài</span><span class="push"></span><button class="btn btn-soft" type="button" data-action="close-contest-create">Hủy</button><button class="btn btn-primary" type="submit" ${!state.contestSelected.length ? "disabled" : ""}>Tạo kỳ thi & lưu danh sách bài</button></div></form></div></section>`;
}

export function renderContestDetail() {
  const c = state.contestDetail;
  if (!c) return "";
  const teacher = state.identity.role === "TEACHER";
  const problems = c.problems || [];
  return `<section class="card" style="margin-bottom:16px"><div class="card-head"><div><h3>${esc(c.contest?.title || "Chi tiết kỳ thi")}</h3><p>${dateTime(c.contest?.startAt)} → ${dateTime(c.contest?.endAt)}</p></div><span class="push"></span>${btn("Đóng", "close-contest-detail", "btn-ghost btn-sm")}</div><div class="card-body"><div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px">${problems.map((p) => `<div class="card contest-card ${!teacher && p.acceptedByMe ? "contest-card-accepted" : ""}"><div class="contest-card-head"><div class="contest-logo">{ }</div><div><h3>${esc(p.id)} · ${esc(p.title)} ${!teacher && p.acceptedByMe ? "✓" : ""}</h3><p>${esc(p.topic || "")} · ${esc(p.difficulty || "")}</p></div>${!teacher && p.acceptedByMe ? pill("AC", "ac") : ""}</div><p>${esc(p.statement || "").slice(0, 200)}</p><div class="footer-row">${btn(!teacher && p.acceptedByMe ? "Xem bài đã AC" : teacher ? "Xem đề" : "Làm bài", "open-contest-problem", p.acceptedByMe && !teacher ? "btn-soft btn-sm" : "btn-primary btn-sm", `data-exercise-id="${esc(p.id)}"`)}</div></div>`).join("")}</div>${teacher ? `<div class="footer-row">${btn("Xem bảng xếp hạng", "open-leaderboard", "btn-soft", `data-contest-id="${esc(c.contest?.id || state.selectedContest?.id || state.selectedContest?.contestId)}"`)}</div>` : ""}</div></section>${teacher&&(c.contest?.distributionMode==="RANDOM"||c.draws?.length)?`<section class="card" style="margin-bottom:16px"><div class="card-head"><h3>Đề đã giao</h3>${btn("Làm mới","select-contest","btn-soft btn-sm",`data-contest-id="${esc(c.contest.id)}"`)}</div><div class="card-body"><p>Danh sách sinh viên và bài đã được phát. Đề đã nhận được giữ nguyên.</p>${(c.draws||[]).map(d=>`<p><strong>${esc(d.displayName)} (${esc(d.username)})</strong> · Bài ${d.ordinal}: ${esc(d.exerciseId)}</p>`).join("")||"Chưa có sinh viên nhận đề."}</div></section>`:""}${teacher?renderExamMonitor(c.contest):""}`;
}

export function renderLeaderboard(entries) {
  return `<div class="table-wrap"><table class="data-table" style="min-width:330px"><thead><tr><th>#</th><th>Học sinh</th><th>Bài AC</th></tr></thead><tbody>${entries.map((e, i) => `<tr><td><strong>${i + 1}</strong></td><td>${esc(e.displayName || e.username || e.userId || "—")}<span class="task-sub">${esc(e.username || "")}</span></td><td>${esc(e.acceptedCount ?? e.acCount ?? e.solvedCount ?? e.acceptedProblems ?? 0)}</td></tr>`).join("")}</tbody></table></div>`;
}

export async function openContest(contestId) {
  const c = state.contests.find((x) => (x.id || x.contestId) === contestId) || {
    id: contestId,
  };
  const d = await assignment("CONTEST_PROBLEMS_GET", { contestId });
  if(state.contestWork?.contestId!==contestId){state.contestWork=null;state.contestCode="";}
  state.selectedContest = c;
  state.contestDetail = d;
  if (state.identity?.role === "TEACHER") {
    try {
      const l = await assignment("LEADERBOARD_GET", { contestId });
      state.leaderboard = l.entries || [];
    } catch (_) {
      state.leaderboard = [];
    }
  } else {
    state.leaderboard = [];
  }
  await loadExam(contestId);
  render();
}

export async function openContestProblem(exerciseId) {
  if (!state.contestDetail) return;
  const problem = (state.contestDetail.problems || []).find(
    (e) => e.id === exerciseId,
  );
  if (!problem) throw new Error("Không tìm thấy đề trong kỳ thi.");
  if (state.identity.role === "TEACHER") {
    state.contestProblemPreview = problem;
    modal(
      `<div class="modal-head"><div><h3>${esc(problem.id)} · ${esc(problem.title)}</h3><p>Chế độ xem trước dành cho giáo viên; chưa ghi thành tích.</p></div><span class="push"></span>${btn("Đóng", "close-modal", "btn-ghost btn-sm")}</div><div class="modal-body"><p>${esc(problem.statement || "")}</p><h4>Đầu vào</h4><p>${esc(problem.inputFormat || "—")}</p><h4>Đầu ra</h4><p>${esc(problem.outputFormat || "—")}</p><h4>Ràng buộc</h4><p>${esc(problem.constraints || "—")}</p></div>`,
    );
    return;
  }
  await loadExam(state.contestDetail.contest?.id||state.selectedContest.id);
  state.contestWork = {
    contestId: state.contestDetail.contest?.id || state.selectedContest.id,
    problem,
    result: null,
  };
  state.contestCode = problem.template || "";
  state.contestSubmissions = [];
  render();
}

export async function submitContestWork() {
  if (!state.contestWork) throw new Error("Chưa chọn bài thi.");
  if (!contestIsLive(state.contestDetail.contest))
    throw new Error("Kỳ thi chưa bắt đầu hoặc đã kết thúc.");
  const r = await assignment("CONTEST_SUBMIT", {
    contestId: state.contestWork.contestId,
    exerciseId: state.contestWork.problem.id,
    sourceCode: state.contestCode,
    idempotencyKey: cryptoRandomId(),
  });
  state.contestWork.result = r.submission;
  state.activeSubmissionPoll = {
    kind: "contest",
    id: r.submission.id,
    tries: 0,
  };
  render();
  toast("Đã nộp bài thi, đang chấm.", "success");
}

let uploadingContestFile=false;
/** File text goes through the same authenticated submission and judging pipeline. */
export async function submitContestFile(file){
  if(!file)return;
  if(uploadingContestFile)throw new Error("Đang đọc file, vui lòng chờ.");
  if(state.identity?.role!=="STUDENT"||!state.contestWork)throw new Error("Chưa chọn bài thi của sinh viên.");
  if(!contestIsLive(state.contestDetail?.contest||{}))throw new Error("Kỳ thi chưa bắt đầu hoặc đã kết thúc.");
  if(!/\.(java|txt)$/i.test(String(file.name||"")))throw new Error("Chỉ hỗ trợ file .java hoặc .txt.");
  if(file.size>32768)throw new Error("File vượt giới hạn 32 KiB.");
  const work=state.contestWork,token=state.token,room=state.room?.roomId;
  uploadingContestFile=true;
  try{
    const code=(await file.text()).replace(/^\uFEFF/,"");
    if(!code.trim()||code.includes("\u0000"))throw new Error("File không có code hợp lệ.");
    if(new TextEncoder().encode(code).length>32768)throw new Error("Code vượt giới hạn 32 KiB.");
    if(token!==state.token||room!==state.room?.roomId||work!==state.contestWork||state.page!=="contests")throw new Error("Bài thi hoặc phiên đăng nhập đã thay đổi; hãy chọn lại file.");
    state.contestCode=code;
    render();
    await submitContestWork();
  }finally{uploadingContestFile=false;}
}
