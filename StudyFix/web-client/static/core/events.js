import {contestAssignmentClick} from "../features/contests/contest-assignment.js";
// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { root } from "./dom.js";
import { classroomClick, classroomInput, loadClassroom } from "../features/classroom/classroom.js";
import { callClick, leaveCall } from "../features/classroom/call.js";
import { markChatReadIfVisible } from "../features/classroom/chat-notifications.js";
import { state } from "./state.js";
import {practiceClick,practiceInput,savePracticeDraft} from "../features/exercises/practice.js";
import {adminClick} from "../features/admin/admin.js";
import {examClick} from "../features/contests/exam-monitoring.js";
import { api, assignment, command } from "./api.js";
import { toast, fail } from "../ui/toast.js";
import { modal, clearModal } from "../ui/modal.js";
import { render } from "../ui/render.js";
import { esc, dateTime } from "../shared/format.js";
import { btn } from "../ui/components.js";
import { login, logout, refreshMe } from "../features/auth/auth.js";
import { startScreenShare, stopScreenShare, playScreenAudio } from "../features/screen-share/screen-share.js";
import {
  commandCreateRoom,
  leaveMeeting,
  leaveRoomMembership,
  fetchDebugExercise,
  joinRoom,
  loadHistory,
  loadHistoryAndFind,
  renderStatementModal,
  replaceStarterCode,
  openStarterLoadConfirm,
  runSharedCheck,
} from "../features/room/room.js";
import {
  openAssignment,
  openSubmission,
  openTeacherTrial,
  reloadSubmissions,
  renderProgressDetail,
  renderStudentProgressDetail,
  submitWork,
  teacherTrialSubmit,
} from "../features/exercises/exercises.js";
import { openContest, openContestProblem, submitContestWork, submitContestFile } from "../features/contests/contests.js";
import {
  loadAssignments,
  loadRooms,
  handleDeletedRoom,
  loadCatalog,
  loadContests,
  loadCurrentRoom,
  loadPageData,
  loadProgress,
  navigate,
  refreshEverything,
} from "./polling.js";

document.addEventListener("click", async (event) => {
  if (event.target?.id === "active-modal" && event.target.classList.contains("modal-backdrop")) {
    clearModal();
    return;
  }
  const el = event.target.closest?.("[data-action]");
  if (!el) return;
  const action = el.dataset.action;
  const d = el.dataset;
  try {
    if(action.startsWith("contest-assign-")||action==="contest-pick-random"){await contestAssignmentClick(action,d);return;}
    if(action.startsWith("exam-")){await examClick(action,d);return;}
    if(action.startsWith("admin-")){await adminClick(action,d);return;}
    if (action.startsWith("call-")) { await callClick(action); return; }
    if (action.startsWith("classroom-")) { await classroomClick(action, d); return; }
    if (action.startsWith("practice-")) { await practiceClick(action,d);return; }
    switch (action) {
      case "chat-open":
        state.meetingPanel="chat";
        await navigate("room");
        el.closest(".chat-toast")?.remove();
        break;
      case "chat-dismiss-toast": el.closest(".chat-toast")?.remove();break;
      case "play-screen-audio": await playScreenAudio();break;
      case "navigate":
        await navigate(d.page);
        break;
      case "toggle-nav":
        document.querySelector(".sidebar")?.classList.toggle("open");
        document
          .querySelector(".mobile-nav-overlay")
          ?.classList.toggle("visible");
        break;
      case "close-nav":
        document.querySelector(".sidebar")?.classList.remove("open");
        document
          .querySelector(".mobile-nav-overlay")
          ?.classList.remove("visible");
        break;
      case "logout":
        await logout();
        break;
      case "refresh":
        await refreshEverything();
        toast("Đã đồng bộ trạng thái.", "success");
        break;
      case "create-room":
        document.getElementById("new-room-code")?.focus();
        break;
      case "rooms-page":
        state.roomPage=Math.max(1,Math.min(state.roomsTotalPages||1,Number(d.page)||1));
        await loadRooms();render();
        break;
      case "join-room":
        await joinRoom(d.roomId || "");
        break;
      case "delete-room": {
        const room=state.rooms.find(r=>String(r.roomId)===d.roomId);
        if(!room||!room.isMine||state.identity.role!=="TEACHER")break;
        const warning=room.memberCount>0?` Phòng đang có ${room.memberCount} người; tất cả sẽ rời phòng và cuộc gọi sẽ kết thúc.`:"";
        if(!confirm(`Xóa phòng “${room.roomCode||room.roomId}”?${warning} Phòng sẽ bị gỡ khỏi danh sách.`))break;
        await command("DELETE_ROOM",{roomId:room.roomId});
        await handleDeletedRoom(room.roomId);
        await refreshMe();await loadRooms();render();
        toast("Đã xóa phòng.","success");
        break;
      }
      case "join-room-overview":
        await joinRoom(
          document.getElementById("overview-room-code")?.value || "",
        );
        break;
      case "refresh-room":
        await refreshMe();
        await loadCurrentRoom();
        await loadAssignments();
        await loadClassroom();
        render();
        toast("Đã đồng bộ code và trạng thái phòng.", "success");
        break;
      case "leave-meeting":
        await leaveMeeting();
        break;
      case "leave-room":
        if(d.roomId===String(state.room?.roomId)&&confirm("Bạn muốn rời phòng này? Bạn cần vào lại nếu muốn tham gia cuộc họp. Phòng vẫn được giữ trong danh sách."))
          await leaveRoomMembership(d.roomId);
        break;
      case "room-tab":
        state.roomTab = d.tab;
        if (d.tab === "screen") await loadCurrentRoom(false);
        render();
        break;
      case "room-side-tab":
        state.sideTab = d.tab;
        render();
        break;
      case "request-edit":
        await command("REQUEST_EDIT");
        setTimeout(() => loadCurrentRoom(false).then(render), 240);
        toast("Đã gửi yêu cầu xin quyền sửa.");
        break;
      case "teacher-request":
        await command("TEACHER_REQUEST_EDIT");
        setTimeout(() => loadCurrentRoom(false).then(render), 240);
        toast("Đã gửi yêu cầu ưu tiên quyền sửa.");
        break;
      case "release-edit":
        await command("RELEASE_EDIT", {
          token: Number(d.token || state.room?.token),
        });
        setTimeout(() => loadCurrentRoom(false).then(render), 240);
        break;
      case "save-shared-code":
        await command("UPDATE_CODE", {
          version: state.room.version,
          token: state.room.token,
          code: document.getElementById("shared-code")?.value || "",
        });
        state.codeDirty = false;
        setTimeout(() => loadCurrentRoom(false).then(render), 400);
        toast("Đã gửi yêu cầu lưu code.", "success");
        break;
      case "replace-shared-code":
      case "open-starter-confirm":
        openStarterLoadConfirm(d.assignmentId || state.activeDebugAssignment?.id);
        break;
      case "confirm-starter-load":
        clearModal();
        await replaceStarterCode(d.assignmentId);
        break;
      case "keep-room-code":
        if (state.starterLoadNotice?.assignmentId === d.assignmentId) state.starterLoadNotice.status = "kept";
        clearModal();
        render();
        toast("Đã giữ nguyên code phòng. Code này chưa được xác nhận là starter của bài mới.", "info");
        break;
      case "defer-starter-load":
        clearModal();
        render();
        toast("Đã để việc nạp starter lại sau. Bài vẫn được giao; code phòng chưa được thay.", "info");
        break;
      case "copy-recovered-draft": {
        const recovery = state.room?.recoverableDraft;
        if (!recovery?.sourceCode)
          throw new Error("Không còn bản nháp để khôi phục.");
        const text = recovery.sourceCode;
        try {
          await navigator.clipboard.writeText(text);
        } catch (_) {
          const area = document.getElementById("recovered-draft-code");
          area?.focus();
          area?.select();
          if (!document.execCommand("copy"))
            throw new Error(
              "Không sao chép tự động được. Hãy chọn toàn bộ code trong vùng khôi phục rồi sao chép thủ công.",
            );
        }
        toast("Đã sao chép code bản nháp để khôi phục.", "success");
        break;
      }
      case "dismiss-recovery":
        await command("ACK_DRAFT_RECOVERY");
        await loadCurrentRoom(false);
        render();
        toast("Đã xác nhận lưu bản nháp khôi phục.", "success");
        break;
      case "edit-lines": {
        const selection =
          document.getElementById("selected-lines")?.value || "";
        const replacement =
          document.getElementById("line-replacement")?.value || "";
        if (!selection.trim()) throw new Error("Nhập dòng cần sửa, ví dụ 4-7.");
        await command("SELECT_LINES", {
          version: state.room.version,
          token: state.room.token,
          selection,
        });
        await command("SET_RANGE", {
          token: state.room.token,
          text: replacement,
        });
        render();
        toast(
          "Đã cập nhật bản nháp trên server. Hãy xem trước trước khi hoàn tất.",
        );
        break;
      }
      case "finish-draft":
        await command("FINISH_DRAFT", {
          token: Number(d.token || state.room?.token),
        });
        setTimeout(() => loadCurrentRoom(false).then(render), 300);
        toast("Đã gửi yêu cầu hoàn tất lượt sửa.");
        break;
      case "cancel-draft":
        await command("CANCEL_DRAFT", {
          token: Number(d.token || state.room?.token),
        });
        setTimeout(() => loadCurrentRoom(false).then(render), 300);
        break;
      case "check-shared":
        await runSharedCheck(d.assignmentId);
        break;
      case "go-debug-assignment":
      case "room-assignments":
        savePracticeDraft();state.exerciseMode="room";
        state.page = "exercises";
        state.catalogMode = action==="go-debug-assignment"?"DEBUG_COLLAB":"";
        state.chosenExercise = null;
        await loadPageData();
        render();
        break;
      case "view-debug-detail": {
        const r = await assignment("ASSIGN_GET", {
          assignmentId: d.assignmentId,
        });
        state.modalDetail = r.assignment;
        modal(renderStatementModal(r.assignment));
        break;
      }
      case "history-code": {
        const v = state.history.find(
          (x) => Number(x.version) === Number(d.version),
        );
        const hist = v || (await loadHistoryAndFind(d.version));
        modal(
          `<div class="modal-head"><div><h3>Lịch sử · Version ${d.version}</h3><p>${esc(hist.editorName || "")} · ${dateTime(hist.createdAt)}</p></div><span class="push"></span>${btn("Đóng", "close-modal", "btn-ghost btn-sm")}</div><div class="modal-body"><pre class="code-editor" style="display:block;white-space:pre;min-height:300px">${esc(hist.code || "")}</pre><p class="subtle-text">${esc(hist.changes || "")}</p></div>`,
        );
        break;
      }
      case "toggle-history":
        state.historyOpen = !state.historyOpen;
        if (state.historyOpen) await loadHistory();
        else render();
        break;
      case "start-screen":
        await startScreenShare();
        break;
      case "stop-screen":
        await stopScreenShare(true);
        render();
        break;
      case "load-catalog":
        await loadCatalog();
        render();
        break;
      case "apply-catalog-filter":
        await loadCatalog();
        render();
        break;
      case "select-exercise": {
        const ex = state.catalog.find((e) => e.id === d.exerciseId);
        if (ex) {
          state.chosenExercise = ex;
          try {
            const r = await assignment("CATALOG_GET", { exerciseId: ex.id });
            state.chosenExercise = {
              ...ex,
              ...r.exercise,
              publicTests: r.publicTests || [],
            };
          } catch (_) {}
          render();
        }
        break;
      }
      case "choose-assignment": {
        const ex = state.catalog.find((e) => e.id === d.exerciseId);
        if (ex) {
          state.chosenExercise = ex;
          try {
            const r = await assignment("CATALOG_GET", { exerciseId: ex.id });
            state.chosenExercise = {
              ...ex,
              ...r.exercise,
              publicTests: r.publicTests || [],
            };
          } catch (_) {}
          state.assignmentScope =
            ex.mode === "DEBUG_COLLAB" ? "ROOM" : state.assignmentScope;
          render();
        }
        break;
      }
      case "teacher-trial-open":
        await openTeacherTrial(d.exerciseId);
        break;
      case "clear-exercise":
        state.chosenExercise = null;
        render();
        break;
      case "load-progress":
        await loadProgress();
        render();
        break;
      case "progress-detail": {
        const p = state.progress.find((x) => x.assignmentId === d.assignmentId);
        if (p) {
          state.progressParentAssignmentId = p.assignmentId;
          clearModal();
          modal(renderProgressDetail(p));
        }
        break;
      }
      case "student-progress":
        clearModal();
        modal(renderStudentProgressDetail(d.userId, d.username));
        break;
      case "student-progress-back": {
        const p = state.progress.find(
          (x) => x.assignmentId === state.progressParentAssignmentId,
        );
        clearModal();
        if (p) modal(renderProgressDetail(p));
        else render();
        break;
      }
      case "open-assignment":
        savePracticeDraft();state.exerciseMode="room";
        state.page="exercises";
        await openAssignment(d.assignmentId);
        break;
      case "close-work":
        state.work = null;
        state.workDetail = null;
        state.workResult = null;
        state.workSubmissions = [];
        state.activeSubmissionPoll = null;
        render();
        break;
      case "reset-work-code":
        if (state.workCodeDirty && state.workCode && !confirm("Nạp code mẫu sẽ thay nội dung editor hiện tại. Tiếp tục?")) break;
        if (state.workDetail) state.workCode = state.workDetail.exercise?.template || "";
        state.workCodeDirty = true;
        render();
        break;
      case "choose-work-code-file":
        document.getElementById("work-code-file")?.click();
        break;
      case "submit-work":
        await submitWork();
        break;
      case "teacher-trial-submit":
        await teacherTrialSubmit();
        break;
      case "reload-submissions":
        await reloadSubmissions();
        break;
      case "submission-detail":
        await openSubmission(d.submissionId);
        break;
      case "filter-student-tasks":
        state.studentTaskQuery =
          document.getElementById("student-task-query")?.value || "";
        state.studentTaskStatus =
          document.getElementById("student-task-status")?.value || "";
        render();
        break;
      case "open-contest-create":
        state.contestFormOpen = true;
        state.contestSelected = [];
        await loadCatalog();
        render();
        break;
      case "close-contest-create":
        state.contestFormOpen = false;
        render();
        break;
      case "load-contests":
        await loadContests();
        render();
        break;
      case "select-contest":
        await openContest(d.contestId);
        break;
      case "close-contest-detail":
        state.contestDetail = null;
        state.selectedContest = null;
        state.contestWork = null;
        render();
        break;
      case "open-leaderboard": {
        if (state.identity?.role !== "TEACHER") throw new Error("Bảng xếp hạng chỉ dành cho giáo viên.");
        const r = await assignment("LEADERBOARD_GET", {
          contestId: d.contestId,
        });
        state.leaderboard = r.entries || [];
        state.selectedContest =
          state.contests.find((x) => (x.id || x.contestId) === d.contestId) ||
          state.selectedContest;
        render();
        break;
      }
      case "open-contest-problem":
        await openContestProblem(d.exerciseId);
        break;
      case "submit-contest-work":
        await submitContestWork();
        break;
      case "close-contest-work":
        state.contestWork = null;
        render();
        break;
      case "reset-contest-code":
        state.contestCode = state.contestWork?.problem?.template || "";
        render();
        break;
      case "close-modal":
        clearModal();
        break;
      default:
        break;
    }
  } catch (e) {
    fail(e);
  }
});
document.addEventListener("studyfix:playback-blocked", () => render());
document.addEventListener("visibilitychange", () => markChatReadIfVisible());
window.addEventListener("focus", () => markChatReadIfVisible());

root.addEventListener("submit", async (event) => {
  const form = event.target;
  event.preventDefault();
  try {
    if (form.id === "login-form") {
      const data = new FormData(form);
      const error = document.getElementById("login-error");
      try {
        await login(data.get("username"), data.get("password"));
      } catch (e) {
        if (error) {
          error.classList.remove("hidden");
          error.textContent = e.message;
        } else fail(e);
      }
    } else if (form.id === "room-search-form") {
      clearTimeout(roomSearchTimer);state.roomSearch=String(new FormData(form).get("q")||"");
      state.roomPage=1;await loadRooms();render();
    } else if (form.id === "create-room-form") {
      const submit=form.querySelector('button[type="submit"]');if(submit)submit.disabled=true;
      try {await commandCreateRoom(new FormData(form).get("roomCode"));}
      finally {if(submit)submit.disabled=false;}
    } else if (form.id === "join-room-form")
      await joinRoom(new FormData(form).get("roomId"));
    else if (form.id === "chat-form") {
      const input = document.getElementById("chat-draft");
      const message = String(input?.value || "").trim();
      if (!message) return;
      await command("CHAT", { text: message });
      state.chatDraft = "";
      if (input) input.value = "";
    } else if (form.id === "assignment-form") {
      const data = new FormData(form);
      const scope =
        state.chosenExercise?.mode === "DEBUG_COLLAB"
          ? "ROOM"
          : data.get("scope") || "ROOM";
      const deadlineMinutes = Number(data.get("deadline") || 0);
      const extra = {
        exerciseId: state.chosenExercise.id,
        scope,
        deadlineMinutes,
      };
      if (scope === "INDIVIDUAL")
        extra.targetUsername = data.get("targetUsername");
      const r = await assignment("ASSIGN_CREATE", extra);
      state.assignmentScope = scope;
      await loadAssignments();
      const created = r.assignment || {};
      if (scope === "ROOM" && (state.chosenExercise?.mode === "DEBUG_COLLAB" || state.chosenExercise?.mode === "DEBUG") && created.id) {
        const matched = state.assignments.find((a) => a.id === created.id);
        state.activeDebugAssignment = { ...created, ...matched, id: created.id, exerciseId: created.exerciseId || state.chosenExercise.id, exerciseTitle: state.chosenExercise.title, mode: created.mode || state.chosenExercise.mode };
        await loadCurrentRoom(false);
        await loadAssignments();
        const finalRow = state.assignments.find((a) => a.id === created.id) || state.activeDebugAssignment;
        state.activeDebugAssignment = finalRow;
        await fetchDebugExercise(finalRow);
        if (r.starterLoadMessage && !r.starterLoaded) {
          state.starterLoadNotice = { assignmentId: finalRow.id, message: r.starterLoadMessage, status: "pending" };
        } else state.starterLoadNotice = null;
        state.page = "room";
        state.meetingClosed=false;
        state.roomTab = "code";
        await loadPageData();
        render();
        toast(r.message || "Đã giao bài sửa lỗi cho phòng.", "success");
        if (state.starterLoadNotice) openStarterLoadConfirm(finalRow.id);
      } else {
        toast(r.message || "Đã giao bài.", "success");
        render();
      }
    } else if (form.id === "contest-form") {
      const data = new FormData(form);
      if (!state.contestSelected.length)
        throw new Error("Chọn ít nhất một bài trong ngân hàng.");
      const result = await assignment("CONTEST_CREATE", {
        title: data.get("title"),
        startInMinutes: Number(data.get("startInMinutes") || 0),
        durationMinutes: Number(data.get("durationMinutes") || 60),
        contestType: "INDIVIDUAL",
        exerciseIds: state.contestSelected,
        distributionMode: data.get("distributionMode")||"ALL",
        problemsPerStudent: Number(data.get("problemsPerStudent")||1),
      });
      state.contestFormOpen = false;
      await loadContests();
      render();
      toast(result.message || "Đã tạo kỳ thi.", "success");
    }
  } catch (e) {
    fail(e);
  }
});

let roomSearchTimer;
function queueRoomSearch(el) {
  state.roomSearch=el.value;state.roomPage=1;
  clearTimeout(roomSearchTimer);
  roomSearchTimer=setTimeout(async()=>{
    if(!state.token||state.page!=="overview")return;
    await loadRooms();render();
  },300);
}
root.addEventListener("compositionend",event=>{
  if(state.exerciseMode!=="room"&&event.target.id==="practice-query")practiceInput(event.target);
  if(event.target.id==="room-search"||(event.target.id==="global-search"&&state.page==="overview"))queueRoomSearch(event.target);
});
root.addEventListener("input", (event) => {
  const el = event.target;
  if (el.form?.id === "contest-form") {
    if (el.name === "title") state.contestTitle = el.value;
    if (el.name === "startInMinutes") state.contestStart = Number(el.value);
    if (el.name === "durationMinutes") state.contestDuration = Number(el.value);
    if (el.name === "problemsPerStudent") state.contestRandomCount = Number(el.value);
    if (el.name === "distributionMode") {state.contestDistribution=el.value;if(event.type!=="input")render();}
  }
  if(el.id==="new-room-code")state.newRoomCode=el.value;
  if(el.id==="overview-room-code")state.joinRoomCode=el.value;
  if(!event.isComposing&&(el.id==="room-search"||(el.id==="global-search"&&state.page==="overview")))queueRoomSearch(el);
  classroomInput(el);
  if (el.id === "shared-code") {
    state.roomBuffer = el.value;
    state.codeDirty = true;
  }
  if (el.id === "work-code") {
    state.workCode = el.value;
    state.workCodeDirty = true;
  }
  if(state.exerciseMode!=="room")practiceInput(el,event.isComposing);
  if (el.id === "contest-code") state.contestCode = el.value;
  if (el.id === "chat-draft") state.chatDraft = el.value;
  if (el.id === "catalog-query") state.catalogQuery = el.value;
  if (el.id === "catalog-topic") state.catalogTopic = el.value;
  if (el.id === "catalog-difficulty") state.catalogDifficulty = el.value;
  if (el.id === "contest-query") state.contestQuery = el.value;
  if (el.id === "student-task-query") state.studentTaskQuery = el.value;
  if (el.id === "student-task-status") state.studentTaskStatus = el.value;
  if (el.id === "global-search") state.globalQuery = el.value;
  if (el.id === "selected-lines") state.selectedLines = el.value;
  if (el.id === "line-replacement") state.lineReplacement = el.value;
});
root.addEventListener("scroll", event => {
  if(event.target.id==="chat-list")markChatReadIfVisible();
  if(event.target.matches?.("#classroom-branch, #classroom-master")) {
    const gutter=event.target.previousElementSibling;if(gutter)gutter.scrollTop=event.target.scrollTop;
  }
},true);
root.addEventListener("keydown", event => {
  if(event.key==="Enter"&&event.target.id==="practice-query"&&!event.isComposing){event.preventDefault();practiceClick("practice-filter",{}).catch(fail);}
  if(event.key==="Tab" && !event.ctrlKey && !event.metaKey && event.target.matches?.("#classroom-branch:not([readonly]), #classroom-master:not([readonly])")) {
    event.preventDefault();const el=event.target;el.setRangeText("    ",el.selectionStart,el.selectionEnd,"end");el.dispatchEvent(new Event("input",{bubbles:true}));
  }
});
window.addEventListener("pagehide", () => {
  if(state.token&&state.room)fetch("/api/auth/departing",{method:"POST",headers:{Authorization:`Bearer ${state.token}`,"Content-Type":"application/json"},body:"{}",keepalive:true}).catch(()=>{});
  if(state.callJoined&&state.token)fetch("/api/call/signal",{method:"POST",headers:{Authorization:`Bearer ${state.token}`,"Content-Type":"application/json"},body:JSON.stringify({kind:"LEAVE"}),keepalive:true}).catch(()=>{});
  leaveCall(false);stopScreenShare(false);
});

root.addEventListener("change", async (event) => {
  const el = event.target;
  if (el.form?.id === "contest-form") {
    if (el.name === "title") state.contestTitle = el.value;
    if (el.name === "startInMinutes") state.contestStart = Number(el.value);
    if (el.name === "durationMinutes") state.contestDuration = Number(el.value);
    if (el.name === "problemsPerStudent") state.contestRandomCount = Number(el.value);
    if (el.name === "distributionMode") {state.contestDistribution=el.value;if(event.type!=="input")render();}
  }
  if(el.id==="practice-query")return; // Text search is handled by input/compositionend, including when render restores focus.
  if(el.id.startsWith("practice-")) {practiceInput(el);try{await practiceClick("practice-filter",{});}catch(e){fail(e);}return;}
  if(el.id==="classroom-history-round"){state.meetingHistoryRound=Number(el.value);return;}
  if (el.id === "classroom-auto-check") { state.classroomAutoCheck = el.checked; return; }
  if(el.id==="contest-code-file"){
    const file=el.files?.[0];el.value="";
    try{await submitContestFile(file);}catch(e){fail(e);}
    return;
  }
  if (el.id === "work-code-file") {
    const file = el.files?.[0];
    el.value = "";
    if (!file) return;
    try {
      const name = String(file.name || "");
      if (!/\.(java|txt)$/i.test(name)) throw new Error("Chỉ hỗ trợ file .java hoặc .txt.");
      if (file.size > 32000) throw new Error("File vượt giới hạn 32 KB.");
      const content = await file.text();
      if (!content.trim()) throw new Error("File không có code.");
      if (state.workCodeDirty && !confirm("Nạp file sẽ thay code bạn đang chỉnh sửa trong editor. Bạn có muốn tiếp tục?")) return;
      state.workCode = content;
      state.workCodeDirty = true;
      savePracticeDraft();
      render();
      toast(`Đã nạp ${name} vào editor. Chưa nộp bài.`, "success");
    } catch (e) { fail(e); }
    return;
  }
  if (el.id === "catalog-mode") {
    state.catalogMode = el.value;
    await loadCatalog();
    render();
  }
  if (el.name === "scope") {
    state.assignmentScope = el.value;
    render();
  }
  if (el.name === "targetUsername") state.assignmentTarget = el.value;
  if (el.matches("[data-contest-exercise]")) {
    const id = el.dataset.contestExercise;
    if (el.checked) {
      if (!state.contestSelected.includes(id)) state.contestSelected.push(id);
    } else
      state.contestSelected = state.contestSelected.filter((x) => x !== id);
    if (state.contestSelected.length > 20) {
      state.contestSelected.pop();
      el.checked = false;
      toast("Mỗi kỳ thi tối đa 20 bài.", "error");
    }
    render();
  }
});


document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && document.getElementById("active-modal")) clearModal();
});
