// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state } from "../../core/state.js";
import { api } from "../../core/api.js";
import { startPolling, stopPolling, loadCurrentRoom, loadPageData, setRoomJoinedHandler } from "../../core/polling.js";
import { render } from "../../ui/render.js";
import { toast,clearJoinRequestNotifications } from "../../ui/toast.js";
import { stopScreenShare } from "../screen-share/screen-share.js";
import { leaveCall } from "../classroom/call.js";
import { resetChatNotifications } from "../classroom/chat-notifications.js";
import {startPresenceSocket,stopPresenceSocket} from "../../core/presence-socket.js";

// Đăng ký hàm xử lý cho api.js khi gặp lỗi 401 và cho polling.js
// khi cần tải lại dữ liệu sau lúc vào phòng. Dùng hàm gọi lại
// để tránh các file import vòng lẫn nhau.
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
    body: { studentCode: username, password },
  });
  state.token = res.token;
  state.peerId = res.peerId;
  state.identity = res.identity;
  state.page=state.identity.role==="TEACHER"?"admin":"overview";
  state.exerciseMode="practice";
  state.meetingClosed=true;
  state.roomId = res.roomId || "";
  sessionStorage.setItem("studyfix.web.token", state.token);
  await refreshMe();
  startPolling();
  await loadCurrentRoom(false);
  await loadPageData();
  state.page = state.identity.role==="TEACHER"?"admin":"overview";
  state.booting = false;
  render();
  toast(`Xin chào ${state.identity.displayName}!`, "success");
}

export async function logout(showToast = true) {
  clearJoinRequestNotifications();
  stopPresenceSocket();
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
  state.pendingAdmission=null;
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
  startPresenceSocket(res.webSocket);
}
