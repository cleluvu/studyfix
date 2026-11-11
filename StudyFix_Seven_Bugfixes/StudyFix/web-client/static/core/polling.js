import {canLeavePage} from "./navigation-guards.js";
// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state, rememberView } from "./state.js";
import { api, assignment } from "./api.js";
import { toast, fail } from "../ui/toast.js";
import { render } from "../ui/render.js";
import { loadClassroom } from "../features/classroom/classroom.js";
import { pollCall, leaveCall } from "../features/classroom/call.js";
import { loadChat } from "../features/classroom/chat-notifications.js";
import {subscribePresence} from "./presence-socket.js";
import {isAdminPage,canNavigate,homePage} from "./roles.js";

// Các hàm đăng ký xử lý để tránh import vòng giữa các module.
// Cần gọi các hàm trong auth.js và screen-share.js từ module này,
// nhưng không import ngược hai file đó
// vì chúng đã import module này, dễ tạo vòng phụ thuộc.
// Thay vào đó, hai file đăng ký hàm gọi lại ở cuối mỗi file.
let roomJoinedHandler = null;
let admissionHandler=null;
export function setAdmissionHandler(fn){admissionHandler=fn;}


let studentHandlers=null;
export function setStudentHandlers(handlers){studentHandlers=handlers;}
let practiceHandlers=null;
let examOrganizationHandlers=null;
export function setExamOrganizationHandlers(handlers){examOrganizationHandlers=handlers;}
let examBankHandlers=null;
export function setExamBankHandlers(handlers){examBankHandlers=handlers;}
let adminHandlers=null;
let examHandlers=null;
export function setExamHandlers(handlers){examHandlers=handlers;}
export function setAdminHandlers(handlers){adminHandlers=handlers;}
export function setPracticeHandlers(handlers){practiceHandlers=handlers;}
export function setRoomJoinedHandler(fn) {
  roomJoinedHandler = fn;
}
let screenSignalHandler = null;
let pollingBusy = false;
export function setScreenSignalHandler(fn) {
  screenSignalHandler = fn;
}


let currentRoomRequest=0;
export function invalidateRoomRequests(){++currentRoomRequest;}
export async function loadCurrentRoom(showErrors = true) {
  if(state.roomLeaving)return null;
  const request=++currentRoomRequest,token=state.token;
  try {
    const res = await api("/api/rooms/current");
    if(request!==currentRoomRequest||token!==state.token||state.roomLeaving)return state.room;
    const next = res.room;
    const presenceChanged=JSON.stringify(state.room?.studentConnections)!==JSON.stringify(next.studentConnections);
    const changed =
      !state.room ||
      state.room.version !== next.version ||
      state.room.roomId !== next.roomId;
    if (
      !state.codeDirty ||
      !state.room ||
      state.room.roomId !== next.roomId ||
      state.room.version === next.version
    )
      state.roomBuffer = next.code;
    state.room = next;
    state.roomId = next.roomId;
    if(presenceChanged&&state.page==="room"&&state.meetingPanel==="people")render();
    if (changed && state.page === "room" && !state.codeDirty) render();
    return next;
  } catch (e) {
    if(request!==currentRoomRequest||token!==state.token||state.roomLeaving)return state.room;
    if (e.status === 401 || e.status === 409) {
      state.room = null;
      state.roomId = "";
      state.classroom = null;
    }
    if (showErrors && !String(e.message).includes("chưa vào phòng")) fail(e);
    return null;
  }
}

let roomDirectoryRequest=0;
export async function handleDeletedRoom(id) {
  if (String(state.room?.roomId||state.roomId||"")!==String(id)) return;
  invalidateRoomRequests();
  state.meetingClosed=true;
  state.room=null;state.roomId="";state.classroom=null;state.page=homePage(state.identity);
  await leaveCall(false);
  if(screenSignalHandler)await screenSignalHandler({kind:"ROOM_CLOSED"});
  toast("Chủ phòng đã xóa phòng. Bạn đã trở về Tổng quan.","info");
}
export async function loadRooms() {
  const request=++roomDirectoryRequest,token=state.token,search=state.roomSearch||"";
  try {
    const res = await api(`/api/rooms?page=${state.roomPage||1}&pageSize=5&q=${encodeURIComponent(search)}`);
    if(request!==roomDirectoryRequest||token!==state.token||search!==(state.roomSearch||""))return false;
    const previous=JSON.stringify([state.rooms,state.roomsTotal,state.roomPage,state.roomsError,state.customRoomCodes]);
    state.rooms = res.rooms || [];
    state.roomPage=res.page||1;state.roomsTotal=res.total??state.rooms.length;state.roomsTotalPages=res.totalPages||1;
    state.customRoomCodes=res.customRoomCodes===true;state.manualRoomDelete=res.manualRoomDelete===true;state.roomsError="";
    if(admissionHandler)await admissionHandler(res.admissions||[]);
    return previous!==JSON.stringify([state.rooms,state.roomsTotal,state.roomPage,state.roomsError,state.customRoomCodes]);
  } catch (e) {
    if(request!==roomDirectoryRequest||token!==state.token||search!==(state.roomSearch||""))return false;
    const changed=state.roomsError!==e.message;state.roomsError=e.message;return changed;
  }
}

export async function loadAssignments() {
  if (!state.room) return;
  try {
    const r = await assignment("ASSIGN_LIST");
    state.assignments = r.assignments || [];
  } catch (e) {
    if (
      state.identity?.role === "TEACHER" &&
      !/chỉ giáo viên chủ phòng/i.test(e.message)
    )
      fail(e);
    state.assignments = [];
  }
}

export async function loadCatalog() {
  if (!state.room || state.identity?.role !== "TEACHER") return;
  try {
    const r = await assignment("CATALOG_LIST", {
      query: state.catalogQuery,
      topic: state.catalogTopic,
      mode: state.catalogMode,
    });
    state.catalog = r.exercises || [];
  } catch (e) {
    state.catalogError = e.message;
    state.catalog = [];
  }
}

export async function loadStudents() {
  if (!state.room) return;
  try {
    const r = await assignment("ROOM_STUDENTS_LIST");
    state.students = r.students || [];
  } catch (_) {
    state.students = [];
  }
}

export async function loadProgress() {
  if (!state.room || state.identity?.role !== "TEACHER") return;
  try {
    const r = await assignment("CLASS_PROGRESS");
    state.progress = r.assignments || [];
  } catch (e) {
    state.progressError = e.message;
    state.progress = [];
  }
}

export async function loadContests() {
  if (!state.room) return;
  try {
    const r = await assignment("CONTEST_LIST");
    state.contests = r.contests || [];
  } catch (e) {
    state.contests = [];
    if (!/Kỳ thi|contest/i.test(e.message)) fail(e);
  }
}

export async function loadPageData() {
  if (!state.identity) return;
  if(state.identity.role==="STUDENT"&&["overview","student-classes","student-ranking","student-progress"].includes(state.page)&&studentHandlers){await studentHandlers.load();await loadRooms();render();return;}
  if(state.page==="contests"&&examOrganizationHandlers)state.page="exam-organization";
  if(state.page==="exam-organization"&&examOrganizationHandlers){await examOrganizationHandlers.load();render();return;}
  if(state.page==="exam-bank"){
    if(state.identity.role!=="TEACHER"){state.page="overview";return;}
    if(examBankHandlers)await examBankHandlers.load();render();return;
  }
  if(state.page==="problem-builder"){state.page="exercises";state.exerciseMode="practice";state.bankView="all";}
  if(isAdminPage(state.page)){
    if(state.identity.role!=="TEACHER"){state.page="overview";return;}
    if(adminHandlers)await adminHandlers.load();render();return;
  }
  if(state.page==="exercises"&&state.exerciseMode!=="room") {
    if(practiceHandlers)await practiceHandlers.load();
    render();return;
  }
  if (!state.room) {
    await loadRooms();
    return;
  }
  try {await loadChat();}catch(e){console.warn("Chat sync failed",e);}
  if (state.page === "overview" || state.page === "room") {
    if(state.page!=="room")await loadAssignments();
    if (state.page === "room") {
      try { await loadClassroom(); } catch(e) { state.classroomError = e.message; }
    }
    if (state.page === "overview") await loadRooms();
  }
  if (state.page === "exercises") {
    if (state.identity.role === "TEACHER")
      await Promise.all([
        loadCatalog(),
        loadAssignments(),
        loadStudents(),
        loadProgress(),
      ]);
    else await loadAssignments();
  }
  if (state.page === "contests") {
    await loadContests();
    if (state.identity.role === "TEACHER") await loadCatalog();
  }
  if(state.page==="room"&&examHandlers)await examHandlers.poll();
  render();
}

export function startPolling() {
  stopPolling();
  state.refreshTimer = setInterval(async () => {
    if (!state.token || pollingBusy) return;
    pollingBusy = true;
    try {
      const r = await api(`/api/events?after=${state.eventCursor}`);
      const events = r.events || [];
      let mustRefresh = false;
      for (const ev of events) {
        state.eventCursor = Math.max(state.eventCursor, Number(ev.id || 0));
        receiveEvent(ev);
        const deleted=String(ev.message||"").trim().match(/^\[ROOM_DELETED\] (\d+)$/);
        if(deleted)await handleDeletedRoom(deleted[1]);
        if (
          /version=|\[COMMITTED\]|\[TEACHER_TAKEOVER\]|\[Thành viên\]|đã vào phòng|đã rời phòng|\[CANCELLED\]/i.test(
            ev.message || "",
          )
        )
          mustRefresh = true;
      }
      if (!state.room && state.roomId) await loadCurrentRoom(false);
      let chatChanged=false;
      if(state.room)try {chatChanged=await loadChat();}catch(e){console.warn("Chat sync failed",e);}
      const roomsChanged=["overview","student-classes"].includes(state.page)?await loadRooms():false;
      if(state.room)try {await pollCall();} catch(e) {state.callError=e.message;}
      const sig = state.room&&!state.meetingClosed ? await api(`/api/screen/signals?after=${state.screenCursor}`) : { signals: [] };
      for (const msg of sig.signals || []) {
        state.screenCursor = Math.max(state.screenCursor, Number(msg.id || 0));
        if (screenSignalHandler) await screenSignalHandler(msg);
      }
      const activePresenter = String(sig.activePresenterPeerId || "");
      if (activePresenter && activePresenter !== state.activeScreenPresenterId && screenSignalHandler) {
        await screenSignalHandler({ kind: "ACTIVE", fromPeerId: activePresenter, payload: sig.activePresenterName || "", roomId: state.room?.roomId || "" });
      } else if (!activePresenter && state.activeScreenPresenterId && state.activeScreenPresenterId !== state.peerId && screenSignalHandler) {
        await screenSignalHandler({ kind: "PRESENCE_END", fromPeerId: state.activeScreenPresenterId, roomId: state.room?.roomId || "" });
      }
      if (mustRefresh && state.room) await loadCurrentRoom(false);
      if (state.page === "room" && state.room) {
        await loadCurrentRoom(false);
        if (state.room) await loadClassroom();
      }
      if (state.activeSubmissionPoll) await pollSubmissionResult();
      if(examOrganizationHandlers)await examOrganizationHandlers.poll();
      const studentChanged=studentHandlers?await studentHandlers.poll():false;
      const practiceChanged=practiceHandlers?await practiceHandlers.poll():false;
      if(examHandlers)await examHandlers.poll();
      const adminChanged=adminHandlers?await adminHandlers.poll():false;
      if(adminChanged&&isAdminPage(state.page))render();
      if (studentChanged||practiceChanged||roomsChanged || chatChanged || events.length || (state.page === "room" && mustRefresh)) render();
    } catch (e) {
      if (state.token && /Phiên|kết nối/i.test(e.message))
        toast(e.message, "error");
    } finally { pollingBusy = false; }
  }, 1600);
}

export function stopPolling() {
  if (state.refreshTimer) clearInterval(state.refreshTimer);
  state.refreshTimer = null;
}

export function receiveEvent(ev) {
  const text = String(ev.message || "").trim();
  if (!text) return;
  state.roomEvents.unshift({ text, time: ev.timestamp || Date.now() });
  state.roomEvents = state.roomEvents.slice(0, 30);
  const match = text.match(/^\[Phòng [^\]]+\]\s+([^:]{1,90}):\s*([\s\S]*)$/);
  if (match && !/^Server$/i.test(match[1].trim())) {
    // loadChat đã đồng bộ ID tin nhắn và người gửi, nên không thêm lại từ sự kiện TCP để tránh trùng.
    return;
  } else if (
    /\[Hệ thống\]|\[TEACHER_TAKEOVER\]|\[COMMITTED\]|\[CANCELLED\]|\[SAVE_FAILED\]|Không tìm thấy phòng|Đã tham gia phòng|Đã tạo và tham gia phòng|version=/.test(
      text,
    )
  ) {
    state.chatItems.push({
      name: "Thông báo phòng",
      text: cleanSystem(text),
      timestamp: ev.timestamp || Date.now(),
      system: true,
    });
    state.chatItems = state.chatItems.slice(-80);
    if (/\[TEACHER_TAKEOVER\]/.test(text) && state.identity?.role === "STUDENT")
      toast(
        "Giáo viên đã ưu tiên quyền sửa. Nếu bạn đang có bản nháp, mở mục khôi phục trong workspace để sao chép trước khi tiếp tục.",
        "info",
      );
    if (/Đã tạo và tham gia phòng:|Đã tham gia phòng:/.test(text))
      // Sau khi vào phòng, hàm đăng ký bằng setRoomJoinedHandler() sẽ gọi refreshMe(), loadCurrentRoom()
      // và loadPageData(). Phần xử lý nằm trong auth.js và được đăng ký tại đây
      // để tránh import vòng giữa polling.js và auth.js.
      setTimeout(() => {
        if (roomJoinedHandler) roomJoinedHandler();
      }, 250);
    if (/Không tìm thấy phòng/.test(text)) toast(text, "error");
  }
}

export function cleanSystem(text) {
  const line = text.split("\n")[0];
  return line.replace(/^\[Phòng [^\]]+\]\s*/, "").slice(0, 280);
}

export async function refreshEverything() {
  if (roomJoinedHandler) await roomJoinedHandler();
  else {
    await loadCurrentRoom(false);
    await loadPageData();
  }
  render();
}

export async function navigate(page) {
  if(page==="contests"&&examOrganizationHandlers)page="exam-organization";
  if(page!==state.page&&!await canLeavePage())return;
  if(page==="admin-attendance")page="admin-connections";
  if(!canNavigate(state.identity,page))return;
  if(page==="exercises")state.exerciseMode="practice";
  state.page = page === "room" && !state.room ? homePage(state.identity) : page;
  if(state.page==="room")state.meetingClosed=false;
  rememberView();
  if (state.page !== "room") state.roomTab = "code";
  await loadPageData();
  subscribePresence();
  render();
  document.querySelector(".sidebar")?.classList.remove("open");
  document.querySelector(".mobile-nav-overlay")?.classList.remove("visible");
}

export async function pollSubmissionResult() {
  const poll = state.activeSubmissionPoll;
  if (!poll) return;
  try {
    if (poll.kind === "personal") {
      const r = await assignment("SUBMISSION_RESULT", {
        submissionId: poll.id,
      });
      const sub = r.submission;
      state.workResult = sub;
      if (
        !["QUEUED", "RUNNING"].includes(String(sub.status).toUpperCase()) &&
        !["QUEUED", "RUNNING"].includes(String(sub.verdict).toUpperCase())
      ) {
        state.activeSubmissionPoll = null;
        const accepted = ["AC", "ACCEPTED", "PASS"].includes(String(sub.verdict || "").toUpperCase());
        const assignmentId = state.workDetail?.id;
        if (accepted && assignmentId) {
          const row = state.assignments.find((a) => a.id === assignmentId);
          if (row) row.acceptedByMe = true;
        }
      } else poll.tries++;
    } else if (poll.kind === "trial") {
      const r = await assignment("TEACHER_TRIAL_RESULT", { trialId: poll.id });
      const trial = r.trial;
      state.workResult = trial;
      if (
        !["QUEUED", "RUNNING"].includes(String(trial.status).toUpperCase()) &&
        !["QUEUED", "RUNNING"].includes(String(trial.verdict).toUpperCase())
      )
        state.activeSubmissionPoll = null;
      else poll.tries++;
    } else {
      const r = await assignment("CONTEST_SUBMISSION_RESULT", {
        submissionId: poll.id,
      });
      state.contestWork.result = r.submission;
      if (
        !["QUEUED", "RUNNING"].includes(String(r.submission.status).toUpperCase()) &&
        !["QUEUED", "RUNNING"].includes(String(r.submission.verdict || "").toUpperCase())
      ) {
        state.activeSubmissionPoll = null;
        if (["AC", "ACCEPTED", "PASS"].includes(String(r.submission.verdict || "").toUpperCase())) {
          const exerciseId = state.contestWork?.problem?.id;
          const problem = state.contestDetail?.problems?.find((p) => p.id === exerciseId);
          if (problem) problem.acceptedByMe = true;
        }
      } else poll.tries++;
    }
    if (poll.tries > 40) state.activeSubmissionPoll = null;
  } catch (e) {
    state.activeSubmissionPoll = null;
    fail(e);
  }
  render();
}
