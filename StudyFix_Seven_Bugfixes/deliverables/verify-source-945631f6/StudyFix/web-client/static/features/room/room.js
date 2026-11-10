// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state, rememberView } from "../../core/state.js";
import { api, assignment, command } from "../../core/api.js";
import { loadCurrentRoom, loadPageData, loadRooms, navigate, invalidateRoomRequests,setAdmissionHandler } from "../../core/polling.js";
import { refreshMe } from "../auth/auth.js";
import { renderScreenWorkspace, stopScreenShare } from "../screen-share/screen-share.js";
import { leaveCall } from "../classroom/call.js";
import { cryptoRandomId, sha256 } from "../../shared/crypto.js";
import { esc, dateTime, timeOnly, initial, roleName, modeLabel, humanVerdict, statusClass } from "../../shared/format.js";
import { btn, pill } from "../../ui/components.js";
import { modal } from "../../ui/modal.js";
import { pageHeader, render } from "../../ui/render.js";
import { toast, fail } from "../../ui/toast.js";
import { renderClassroom } from "../classroom/classroom.js";
import {homePage} from "../../core/roles.js";
document.addEventListener("studyfix:presence",event=>{
  if(state.identity?.role!=="TEACHER"||!state.room||String(event.detail?.roomId)!==String(state.room.roomId))return;
  const rows=event.detail.connections||[];
  if(JSON.stringify(rows)===JSON.stringify(state.room.studentConnections))return;
  state.room.studentConnections=rows;
  if(state.page==="room"&&state.meetingPanel==="people")render();
});

export function renderRoom() {
  if (!state.room) return "";
  return renderClassroom(renderChat(), renderMembers());
}

export async function leaveMeeting() {
  if(state.identity?.role==="STUDENT"&&state.room){await leaveRoomMembership(state.room.roomId);return;}
  state.meetingClosed=true;state.page=homePage(state.identity);rememberView();
  await leaveCall();
  await stopScreenShare(true);
  state.meetingPanel="";
  await navigate(homePage(state.identity));
}

export async function leaveRoomMembership(roomId) {
  if(state.roomLeaving||!state.room||String(state.room.roomId)!==String(roomId))return;
  state.roomLeaving=true;
  state.meetingClosed=true;
  invalidateRoomRequests();
  try {
    await leaveCall();
    await stopScreenShare(true);
    await command("LEAVE_ROOM");
    state.room=null;state.roomId="";state.classroom=null;
    state.assignments=[];state.students=[];state.progress=[];state.contests=[];
    state.meetingPanel="";state.page=homePage(state.identity);
    rememberView();
    await refreshMe();
    await loadRooms();
    await loadPageData();
    render();
    toast("Đã rời phòng. Bạn có thể tham gia phòng khác.","success");
  } finally {state.roomLeaving=false;}
}

export function renderCollabWorkspace(isOwner, isEditor, queue) {
  const debugAssignments = state.assignments.filter(
    (a) => a.mode === "DEBUG_COLLAB" || a.mode === "DEBUG",
  );
  const activeDebug =
    debugAssignments.find((a) => a.id === state.activeDebugAssignment?.id) ||
    debugAssignments.slice().sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0))[0] ||
    state.activeDebugAssignment || null;
  const debugExercise = state.debugExercise || null;
  return `<div class="room-layout"><div class="room-center">
    <div class="card collab-toolbar"><span class="chip blue">Version ${state.room.version}</span><span class="chip ${isEditor ? "green" : "neutral"}">${isEditor ? "● Bạn đang giữ quyền sửa" : state.room.editTurnActive ? "Đang có người sửa" : "Chưa có người giữ quyền"}</span><span class="toolbar-version">${queue.length ? `${queue.length} đang chờ` : "Hàng đợi trống"}</span>
      ${isOwner ? btn("✎ Ưu tiên quyền sửa", "teacher-request", "btn-primary btn-sm") : btn("✎ Xin quyền sửa", "request-edit", "btn-primary btn-sm")}
      ${isEditor ? btn("Trả quyền", "release-edit", "btn-soft btn-sm", `data-token="${state.room.token}"`) : ""}
      ${isOwner && activeDebug ? btn("↺ Khôi phục code ban đầu", "open-starter-confirm", "btn-soft btn-sm", `data-assignment-id="${esc(activeDebug.id)}"`) : ""}
      ${isOwner ? btn("＋ Giao bài sửa lỗi", "go-debug-assignment", "btn-teal btn-sm") : ""}
    </div>
    ${renderRecoveryDraft()}
    ${renderDebugStatement(activeDebug, debugExercise, isOwner)}
    <section class="card code-card"><div class="card-head"><div class="stat-icon" style="width:31px;height:31px;border-radius:8px;font-size:14px">⌘</div><div><h3>Code cộng tác</h3><p>${isEditor ? "Bạn đang giữ lượt sửa. Học sinh chỉnh sửa theo vùng đã chọn; giáo viên có thể thay toàn bộ code." : "Mã nguồn chung được đồng bộ tự động từ server."}</p></div><span class="push"></span>${pill(isEditor ? "EDITING" : "SYNCED", isEditor ? "easy" : "neutral")}</div>
      <textarea id="shared-code" class="code-editor" spellcheck="false" aria-label="Code chung" ${isOwner && isEditor ? "" : "readonly"}>${esc(state.roomBuffer ?? state.room.code ?? "")}</textarea>
      <div class="code-actions"><small>Version ${state.room.version} · ${state.room.draftActive ? "Có bản nháp chưa commit" : "Không có bản nháp trên server"}</small>
      ${isOwner && isEditor ? btn("Lưu code toàn bộ", "save-shared-code", "btn-primary btn-sm") : ""}
      ${!isOwner && isEditor ? `<input id="selected-lines" class="field" style="width:120px;padding:7px 9px;font-size:10px" placeholder="Dòng 4-7" value="${esc(state.selectedLines || "")}" /><input id="line-replacement" class="field" style="width:min(240px,100%);padding:7px 9px;font-size:10px" placeholder="Nội dung thay thế" value="${esc(state.lineReplacement || "")}" />${btn("Sửa vùng đã chọn", "edit-lines", "btn-soft btn-sm")}${btn("Hoàn tất lượt sửa", "finish-draft", "btn-primary btn-sm", `data-token="${state.room.token}"`)}${btn("Hủy nháp", "cancel-draft", "btn-danger btn-sm", `data-token="${state.room.token}"`)}` : ""}
      ${activeDebug ? btn("Kiểm tra code chung", "check-shared", "btn-teal btn-sm", `data-assignment-id="${esc(activeDebug.id)}" ${state.room.draftActive ? "disabled" : ""}`) : `<span class="subtle-text">Giao bài sửa lỗi để bật kiểm tra code chung.</span>`}
    </div></section>
    ${state.sharedCheck ? renderSharedCheck(state.sharedCheck) : ""}
    <details class="card history-panel" ${state.historyOpen ? "open" : ""}><summary class="history-summary" data-action="toggle-history">▤ Lịch sử phiên bản <span class="push">${state.room.history.length} phiên bản · bấm để xem</span></summary><div class="history-list">${
      state.room.history.length
        ? state.room.history
            .slice()
            .reverse()
            .map(
              (h) =>
                `<div class="history-entry"><strong>v${h.version}</strong><div>${esc(h.editorName || "—")}<small>${dateTime(h.createdAt)} · ${esc((h.changes || "").split("\n")[0])}</small></div>${btn("Xem code", "history-code", "btn-soft btn-sm", `data-version="${h.version}"`)}</div>`,
            )
            .join("")
        : `<div class="empty-slim">Chưa có lịch sử.</div>`
    }</div></details>
  </div><aside class="room-sidebar"><section class="card"><div class="card-head"><div><h3>Phòng học</h3><p>${esc(state.room.roomId)} · ${membersCountText()}</p></div><span class="push"></span><span class="chip">${state.room.members.length} người</span></div><div class="card-body" style="padding:12px 13px"><div class="side-tabs"><button class="side-tab ${state.sideTab === "chat" ? "active" : ""}" data-action="room-side-tab" data-tab="chat">Chat</button><button class="side-tab ${state.sideTab === "members" ? "active" : ""}" data-action="room-side-tab" data-tab="members">Thành viên (${state.room.members.length})</button></div></div>${state.sideTab === "chat" ? renderChat() : renderMembers()}</section>
    ${isOwner ? `<section class="card"><div class="card-head"><div><h3>Quyền sửa</h3><p>Điều phối lượt sửa trong phòng</p></div></div><div class="card-body"><div class="assignment-row"><div class="task-number">✎</div><div class="assignment-row-main"><strong>${state.room.members.find((m) => m.peerId === state.room.editorPeerId)?.displayName || (state.room.editTurnActive ? "Một client đang sửa" : "Chưa có người giữ quyền")}</strong><small>${state.room.editTurnActive ? "Quyền đang được cấp" : "Có thể ưu tiên quyền sửa"}</small></div></div>${queue.length ? `<div class="subtle-text" style="margin-top:12px">Hàng đợi: ${queue.length} lượt đang chờ · Giáo viên có thể ưu tiên trực tiếp.</div>` : `<div class="subtle-text" style="margin-top:12px">Hiện chưa có hàng đợi.</div>`}</div></section>` : ""}</aside></div>`;
}

export function membersCountText() {
  return `${state.room.members.filter((m) => m.online).length} đang trực tuyến`;
}

export function renderDebugStatement(assignmentRow, exercise, isOwner) {
  if (!assignmentRow)
    return `<div class="problem-strip"><div class="mini-title">Bài tập đang làm</div><h3>Chưa giao bài sửa lỗi cùng phòng</h3><p>Giáo viên chọn bài có loại “Sửa lỗi cùng phòng” trong Ngân hàng bài và giao cho cả phòng. Khi giao thành công, code lỗi mẫu được tự nạp nếu trạng thái phòng an toàn.</p>${isOwner ? btn("Chọn bài từ ngân hàng", "go-debug-assignment", "btn-primary btn-sm") : ""}</div>`;
  const statement =
    exercise?.exercise?.statement ||
    exercise?.statement ||
    "Đề bài được hiển thị từ ngân hàng bài tập.";
  const starterNoticeState = state.starterLoadNotice && state.starterLoadNotice.assignmentId === assignmentRow.id
    ? state.starterLoadNotice : null;
  const starterNotice = starterNoticeState ? `<div class="notice-box ${starterNoticeState.status === "kept" ? "" : "warn"}" style="margin-top:12px"><strong>${starterNoticeState.status === "kept" ? "Đang giữ code phòng hiện tại" : "Bài đã giao, code chung chưa được đồng bộ."}</strong><p>${esc(starterNoticeState.status === "kept" ? "Code hiện tại được giữ nguyên, chưa được xác nhận là starter của bài này." : starterNoticeState.message || "Code phòng khác starter code của bài này.")}</p><div class="tool-row">${isOwner ? btn("Nạp starter của bài này", "open-starter-confirm", "btn-primary btn-sm", `data-assignment-id="${esc(assignmentRow.id)}"`) : ""}${isOwner && starterNoticeState.status !== "kept" ? btn("Giữ code phòng", "keep-room-code", "btn-soft btn-sm", `data-assignment-id="${esc(assignmentRow.id)}"`) : ""}</div></div>` : "";
  return `<div class="problem-strip"><div class="mini-title">Mô tả đề · ${esc(assignmentRow.exerciseId)}</div><h3>${esc(assignmentRow.exerciseTitle || exercise?.exercise?.title || assignmentRow.exerciseId)}</h3><p>${esc(statement)}</p><div class="tool-row" style="margin-top:10px">${pill("Sửa lỗi cùng phòng", "debug")}${pill(assignmentRow.deadlineAt ? `Hạn ${dateTime(assignmentRow.deadlineAt)}` : "Không hạn", "neutral")}${btn("Xem mô tả đầy đủ", "view-debug-detail", "btn-soft btn-sm", `data-assignment-id="${esc(assignmentRow.id)}"`)}</div>${starterNotice}</div>`;
}

export function renderRecoveryDraft() {
  const recovery = state.room?.recoverableDraft;
  if (!recovery?.sourceCode) return "";
  return `<section class="card recovery-card"><div class="card-head"><div><h3>Đã giữ lại bản nháp của lượt sửa trước</h3><p>Bản khôi phục riêng cho tài khoản của bạn · dựa trên version ${esc(recovery.baseVersion)} · ${dateTime(recovery.savedAt)}</p></div><span class="push"></span>${btn("Sao chép code", "copy-recovered-draft", "btn-primary btn-sm")}${btn("Đã lưu bản nháp", "dismiss-recovery", "btn-soft btn-sm")}</div><div class="card-body"><div class="notice-box warn">Giáo viên đã ưu tiên quyền sửa. Quyền/token cũ bị vô hiệu hóa, nhưng code nháp của bạn vẫn còn ở đây cho đến khi bạn xác nhận đã lưu hoặc rời phòng.</div><textarea id="recovered-draft-code" class="code-editor recovery-code" spellcheck="false" readonly aria-label="Bản nháp cần khôi phục">${esc(recovery.sourceCode)}</textarea></div></section>`;
}

export function renderSharedCheck(run) {
  return `<section class="card submission-panel"><div class="submission-status"><strong>Kiểm tra code chung</strong>${pill(humanVerdict(run.verdict || run.status), statusClass(run.verdict || run.status))}<span class="subtle-text">Version ${run.checkedVersion ?? state.room.version} · ${run.passedTests ?? 0}/${run.totalTests ?? 0} test đạt</span><span class="push"></span><small class="subtle-text">${run.durationMs ? `${run.durationMs} ms` : ""}</small></div><p class="subtle-text" style="margin:10px 0 0">${esc(run.feedback || "Server đang chấm snapshot code đã xác minh.")}</p></section>`;
}

export function renderChat() {
  const messages = state.chatItems.slice(-35);
  return `<div class="chat-list" id="chat-list">${messages.length ? messages.map((m) => (m.system ? `<div class="system-note">${esc(m.text)} <span class="time">${timeOnly(m.timestamp)}</span></div>` : `<div class="chat-item"><div class="chat-avatar">${esc(initial(m.name))}</div><div class="chat-bubble"><div class="chat-meta"><strong>${esc(m.name)}</strong><time>${timeOnly(m.timestamp)}</time></div><p>${esc(m.text)}</p></div></div>`)).join("") : `<div class="empty-slim">Chưa có tin nhắn. Hãy bắt đầu trao đổi với phòng học.</div>`}</div><form id="chat-form" class="chat-compose"><input class="field" id="chat-draft" name="message" placeholder="Nhập tin nhắn…" maxlength="2000" value="${esc(state.chatDraft)}" autocomplete="off" /><button class="btn btn-primary" type="submit" title="Gửi">➤</button></form>`;
}

export function renderMembers() {
  return `<div class="member-list">${state.room.members.map((m) => `<div class="member-item"><div class="avatar">${esc(initial(m.displayName))}</div><div class="member-copy"><strong>${esc(m.displayName)} ${m.isTeacherOwner ? '<span style="color:#d99721">♛</span>' : ""}</strong><small>${roleName(m.role)}${m.isEditor ? " · Đang sửa" : m.inQueue ? " · Đang chờ" : ""}</small></div><span class="presence ${m.online ? "" : "offline"}"></span></div>`).join("") || `<div class="empty-slim">Không có thành viên.</div>`}</div>`;
}

export async function fetchDebugExercise(row) {
  if (!row) return;
  try {
    state.debugExercise = await assignment("ASSIGN_GET", {
      assignmentId: row.id,
    });
  } catch (e) {
    state.debugExercise = null;
  }
}

export async function loadHistory() {
  const r = await api("/api/rooms/current/history");
  state.history = r.history || [];
  state.historyOpen = true;
  render();
}

export async function runSharedCheck(assignmentId) {
  const room = state.room;
  if (!room) throw new Error("Bạn chưa vào phòng.");
  if (room.draftActive)
    throw new Error("Hoàn tất hoặc hủy lượt sửa trước khi kiểm tra.");
  const row = state.assignments.find((a) => a.id === assignmentId);
  if (!row) throw new Error("Không tìm thấy bài sửa lỗi đang giao.");
  const source = room.code || "";
  const sourceHash = await sha256(source);
  const result = await assignment("SHARED_CHECK_CREATE", {
    assignmentId,
    checkRequestId: cryptoRandomId(),
    roomCodeVersion: room.version,
    sourceHash,
  });
  state.sharedCheck = result.checkRun || result;
  render();
  const id = state.sharedCheck.id;
  let tries = 0;
  const poll = async () => {
    if (!state.sharedCheck || state.sharedCheck.id !== id || tries++ > 35)
      return;
    try {
      const r = await assignment("SHARED_CHECK_STATUS", { checkRunId: id });
      state.sharedCheck = r.checkRun || r;
      render();
      if (
        ["QUEUED", "RUNNING"].includes(
          String(state.sharedCheck.status).toUpperCase(),
        )
      )
        setTimeout(poll, 1800);
    } catch (e) {
      fail(e);
    }
  };
  setTimeout(poll, 1500);
  toast("Đã gửi snapshot code chung để chấm.", "success");
}

export async function commandCreateRoom(value) {
  const code=String(value||"").normalize("NFC").trim();
  if(!code||Array.from(code).length>80||/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(code))throw new Error("Nhập mã phòng từ 1 đến 80 ký tự, không xuống dòng.");
  if(!state.customRoomCodes)throw new Error("Hãy khởi động lại Server Manager để bật tạo phòng bằng mã tự chọn.");
  await command("CREATE_ROOM",{roomCode:code});
  await refreshMe();
  await loadCurrentRoom(false);
  state.page = "room";
  state.meetingClosed=false;
  state.roomTab = "code";
  state.newRoomCode="";
  await loadPageData();
  render();
  toast(`Đã tạo phòng ${code}.`, "success");
}

export async function joinRoom(roomId) {
  const id = String(roomId || "").trim();
  if (!id) throw new Error("Nhập mã phòng.");
  const result=await command("JOIN_ROOM", { roomId: id });
  if(result.admission?.status==="PENDING"){
    state.pendingAdmission={classId:result.admission.classId,roomCode:result.admission.roomCode,requestId:result.admission.requestId,userId:state.identity?.userId};
    await loadRooms();render();
    toast("Đã gửi yêu cầu. Đang chờ giảng viên chấp nhận.","info");return;
  }
  state.pendingAdmission=null;
  await new Promise((r) => setTimeout(r, 350));
  await refreshMe();
  const room = await loadCurrentRoom(false);
  if (room) {
    state.joinRoomCode="";
    state.page = "room";
    state.meetingClosed=false;
    await loadPageData();
    render();
    toast(`Đã vào phòng ${room.roomCode||room.roomId}.`, "success");
  } else {
    const last =
      state.roomEvents[0]?.text || "Không vào được phòng. Kiểm tra mã phòng.";
    toast(last, "error");
  }
}

export async function handleAdmissionUpdate(rows){
  const pending=state.pendingAdmission;
  if(state.identity?.role!=="STUDENT"||!pending||pending.userId!==state.identity.userId)return;
  const row=rows.find(r=>r.classId===pending.classId&&(!pending.requestId||r.requestId===pending.requestId));if(!row)return;
  if(row.status==="APPROVED"){
    state.pendingAdmission=null;
    if(!state.room){toast("Giảng viên đã chấp nhận. Đang vào lớp…","success");await joinRoom(pending.roomCode);}
  }else if(["REJECTED","LEFT","CANCELLED","UNAVAILABLE"].includes(row.status)){
    state.pendingAdmission=null;
    toast(row.status==="REJECTED"?"Giảng viên đã từ chối yêu cầu vào lớp.":"Lớp không còn khả dụng hoặc quyền tham gia đã thay đổi.","error");
    await loadRooms();render();
  }
}
setAdmissionHandler(handleAdmissionUpdate);
document.addEventListener("studyfix:admission-update",event=>{handleAdmissionUpdate(event.detail||[]).catch(fail);});

export function openStarterLoadConfirm(assignmentId) {
  const row = state.assignments.find((a) => a.id === assignmentId) ||
    (state.activeDebugAssignment?.id === assignmentId ? state.activeDebugAssignment : null);
  if (!row) throw new Error("Không tìm thấy chính xác bài sửa lỗi vừa giao. Hãy tải lại danh sách bài của phòng.");
  if (row.mode !== "DEBUG_COLLAB" && row.mode !== "DEBUG") throw new Error("Chỉ bài sửa lỗi cùng phòng mới có starter code để khôi phục.");
  state.activeDebugAssignment = row;
  modal(`<div class="modal-head"><div><h3>Đồng bộ code bài ${esc(row.exerciseId)}</h3><p>${esc(row.exerciseTitle || "Bài sửa lỗi cùng phòng")}</p></div><span class="push"></span>${btn("Đóng", "close-modal", "btn-ghost btn-sm")}</div><div class="modal-body"><div class="notice-box warn">Bài đã được giao. Nạp starter sẽ thay toàn bộ code chung trong phòng bằng code gốc của đúng bài này. Nếu có lượt sửa hoặc bản nháp chưa hoàn tất, server sẽ từ chối thay code để tránh mất dữ liệu.</div><div class="footer-row">${btn("Nạp starter của bài vừa giao", "confirm-starter-load", "btn-primary", `data-assignment-id="${esc(row.id)}"`)}${btn("Giữ code phòng", "keep-room-code", "btn-soft", `data-assignment-id="${esc(row.id)}"`)}${btn("Để sau", "defer-starter-load", "btn-ghost")}</div></div>`);
}

export async function replaceStarterCode(assignmentId) {
  const row = assignmentId
    ? (state.assignments.find((a) => a.id === assignmentId) ||
       (state.activeDebugAssignment?.id === assignmentId ? state.activeDebugAssignment : null))
    : state.activeDebugAssignment;
  if (!row?.id) throw new Error("Không tìm thấy đúng bài sửa lỗi được chọn. Hãy tải lại danh sách bài của phòng; hệ thống sẽ không tự chọn một bài khác.");
  if (row.mode !== "DEBUG_COLLAB" && row.mode !== "DEBUG") throw new Error("Bài được chọn không phải bài sửa lỗi cùng phòng.");
  const prepared = await assignment("DEBUG_STARTER_PREPARE", { assignmentId: row.id });
  if (prepared.assignmentId !== row.id) throw new Error("Server trả starter không thuộc bài đang chọn; đã hủy thao tác để tránh nạp nhầm code.");
  if (prepared.draftActive || prepared.editTurnActive)
    throw new Error("Phòng đang có lượt sửa hoặc bản nháp. Hãy hoàn tất/hủy lượt sửa rồi xác nhận nạp lại; code hiện tại chưa bị thay.");
  await loadCurrentRoom(false);
  if (!state.room || Number(prepared.roomCodeVersion) !== Number(state.room.version))
    throw new Error("Version code đã thay đổi. Hãy đồng bộ trạng thái phòng rồi thử lại; code chưa bị thay.");
  const result = await assignment("DEBUG_STARTER_LOAD", {
    assignmentId: row.id,
    expectedVersion: prepared.roomCodeVersion,
  });
  state.starterLoadNotice = null;
  state.activeDebugAssignment = row;
  state.sharedCheck = null;
  await loadCurrentRoom(false);
  await fetchDebugExercise(row);
  render();
  toast(result.message || "Đã khôi phục code ban đầu của bài đang giao.", "success");
}

export async function loadHistoryAndFind(version) {
  const r = await api("/api/rooms/current/history");
  state.history = r.history || [];
  return state.history.find((x) => Number(x.version) === Number(version)) || {};
}

export function renderStatementModal(a) {
  const e = a.exercise || {};
  return `<div class="modal-head"><div><h3>${esc(e.id || a.exerciseId)} · ${esc(e.title || a.exerciseTitle || "Đề bài")}</h3><p>${modeLabel(a.mode)}</p></div><span class="push"></span>${btn("Đóng", "close-modal", "btn-ghost btn-sm")}</div><div class="modal-body"><h4>Mô tả</h4><p>${esc(e.statement || "")}</p><h4>Input</h4><p>${esc(e.inputFormat || "—")}</p><h4>Output</h4><p>${esc(e.outputFormat || "—")}</p><h4>Ràng buộc</h4><p>${esc(e.constraints || "—")}</p></div>`;
}
