// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

import { state } from "./state.js";
import { api, assignment } from "./api.js";
import { toast, fail } from "../ui/toast.js";
import { render } from "../ui/render.js";
import { loadClassroom } from "../features/classroom/classroom.js";
import { pollCall } from "../features/classroom/call.js";

// --- Small dependency-injection hooks -------------------------------------
// features/auth/auth.js and features/screen-share/screen-share.js both need to
// be *called* from this module, but this module must not *import* them back
// (that would create an import cycle, since both of them import from here).
// They register a callback instead; see the bottom of each of those files.
let roomJoinedHandler = null;
export function setRoomJoinedHandler(fn) {
  roomJoinedHandler = fn;
}
let screenSignalHandler = null;
let pollingBusy = false;
export function setScreenSignalHandler(fn) {
  screenSignalHandler = fn;
}


export async function loadCurrentRoom(showErrors = true) {
  try {
    const res = await api("/api/rooms/current");
    const next = res.room;
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
    if (changed && state.page === "room" && !state.codeDirty) render();
    return next;
  } catch (e) {
    if (e.status === 401 || e.status === 409) {
      state.room = null;
      state.roomId = "";
      state.classroom = null;
    }
    if (showErrors && !String(e.message).includes("chưa vào phòng")) fail(e);
    return null;
  }
}

export async function loadRooms() {
  try {
    const res = await api("/api/rooms");
    state.rooms = res.rooms || [];
  } catch (_) {
    state.rooms = [];
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
  if (!state.room) {
    await loadRooms();
    return;
  }
  if (state.page === "overview" || state.page === "room") {
    await loadAssignments();
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
        if (
          /version=|\[COMMITTED\]|\[TEACHER_TAKEOVER\]|\[Thành viên\]|đã vào phòng|đã rời phòng|\[CANCELLED\]/i.test(
            ev.message || "",
          )
        )
          mustRefresh = true;
      }
      if (!state.room && state.roomId) await loadCurrentRoom(false);
      if(state.room)try {await pollCall();} catch(e) {state.callError=e.message;}
      const sig = state.room ? await api(`/api/screen/signals?after=${state.screenCursor}`) : { signals: [] };
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
      if (events.length || (state.page === "room" && mustRefresh)) render();
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
    state.chatItems.push({
      name: match[1].trim(),
      text: match[2],
      timestamp: ev.timestamp || Date.now(),
      system: false,
    });
    state.chatItems = state.chatItems.slice(-80);
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
      // See setRoomJoinedHandler() below: the actual refreshMe()/loadCurrentRoom()/
      // loadPageData() chain lives in features/auth/auth.js, which registers itself
      // here instead of being imported directly (avoids polling<->auth cycle).
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
  await refreshMe();
  await loadCurrentRoom(false);
  await loadPageData();
  render();
}

export async function navigate(page) {
  state.page = page;
  if (page !== "room") state.roomTab = "code";
  await loadPageData();
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
