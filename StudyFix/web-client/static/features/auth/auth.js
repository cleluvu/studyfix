// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { startPolling, stopPolling, loadCurrentRoom, loadPageData, setRoomJoinedHandler } from "../../core/polling.js";
import { render } from "../../ui/render.js";
import { toast } from "../../ui/toast.js";
import { stopScreenShare } from "../screen-share/screen-share.js";
import { leaveCall } from "../classroom/call.js";
import { resetChatNotifications } from "../classroom/chat-notifications.js";

// Wiring for core/api.js (401 -> logout) and core/polling.js (post-join
// refresh) — see the comments in those files for why this is a registered
// callback instead of a direct import.
document.addEventListener("studyfix:unauthorized", () => {
  logout(false);
});
setRoomJoinedHandler(() => {
  return refreshMe()
    .then(() => loadCurrentRoom(false))
    .then(() => loadPageData());
});


export async function login(username, password) {
  const res = await api("/api/auth/login", {
    method: "POST",
    body: { username, password },
  });
  state.token = res.token;
  state.peerId = res.peerId;
  state.identity = res.identity;
  state.exerciseMode="practice";
  state.meetingClosed=true;
  state.roomId = res.roomId || "";
  sessionStorage.setItem("studyfix.web.token", state.token);
  await refreshMe();
  startPolling();
  await loadCurrentRoom(false);
  await loadPageData();
  state.page = "overview";
  state.booting = false;
  render();
  toast(`Xin chào ${state.identity.displayName}!`, "success");
}

export async function logout(showToast = true) {
  await leaveCall(false);
  const token = state.token;
  stopScreenShare(false);
  state.token = "";
  state.identity = null;
  state.peerId = "";
  state.room = null;
  state.meetingClosed=true;
  state.classroom = null;
  state.classroomDrafts = {};
  state.meetingView = "";state.meetingPanel="";state.callRoster=[];
  state.roomId = "";
  state.assignments = [];
  state.catalog = [];
  state.chatItems = [];
  resetChatNotifications();
  state.page = "overview";
  state.eventCursor = 0;
  state.screenCursor = 0;
  sessionStorage.removeItem("studyfix.web.token");
  sessionStorage.removeItem("studyfix.web.view");
  stopPolling();
  if (token)
    fetch("/api/auth/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => {});
  render();
  if (showToast) toast("Bạn đã đăng xuất.");
}

export async function refreshMe() {
  const res = await api("/api/me");
  state.identity = res.identity;
  state.peerId = res.peerId;
  state.roomId = res.roomId || "";
}
