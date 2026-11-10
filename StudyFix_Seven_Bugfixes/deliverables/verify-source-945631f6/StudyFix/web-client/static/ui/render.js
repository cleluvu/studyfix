// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state, rememberView } from "../core/state.js";
import { root, bindVideoElements, captureMediaElements, restoreMediaElements } from "../core/dom.js";
import { renderPage } from "../core/router.js";
import { esc, dateTime, initial, roleName, modeLabel } from "../shared/format.js";
import { btn, pill } from "./components.js";
import { unreadChatCount, markChatReadIfVisible, updateChatBadges } from "../features/classroom/chat-notifications.js";
import {teacherPageLabels} from "../core/roles.js";

export function layout(content) {
  if (state.page === "room" && state.room) return content;
  const callOverlay=state.callJoined?`<aside class="floating-call"><strong>Cuộc gọi đang bật</strong><button data-action="navigate" data-page="room">Về phòng</button><button data-action="call-mic">${state.callLocal?.getAudioTracks().some(t=>t.enabled)?"Tắt mic":"Bật mic"}</button><button data-action="call-camera">${state.callLocal?.getVideoTracks().length?"Tắt camera":"Bật camera"}</button><button data-action="call-leave">Ngắt gọi</button></aside><div class="meeting-audio-bank">${(state.callRoster||[]).filter(p=>p.peerId!==state.peerId).map(p=>`<audio data-call-audio="${esc(p.peerId)}" autoplay></audio>`).join("")}</div>`:"";
  const nav = [
    ["overview", "⌂", "Lớp học của tôi"],
    ["exercises", "▤", "Luyện code"],
    ["contests", "♜", "Thi & xếp hạng"],
  ];
  if(state.identity?.role==="TEACHER"){
    nav[0]=["overview","⌘","Phòng học trực tuyến"];
    nav.unshift(...Object.entries(teacherPageLabels).filter(([id])=>id!=="admin-assignments").map(([id,label])=>[id,id==="admin"?"⌂":"▤",label]));
  }
  const unread=unreadChatCount();
  return `<div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand"><div class="brand-mark">&lt;/&gt;</div><div><h1>StudyFix</h1><p>Học cùng nhau · Tiến bộ mỗi ngày</p></div></div>
      <div class="side-label">${state.identity?.role==="TEACHER"?"Quản lý & giảng dạy":"Không gian học tập"}</div>
      <nav class="nav">${nav.map(([id, icon, label]) => `<button class="nav-btn ${state.page === id ? "active" : ""}" data-action="navigate" data-page="${id}"><span class="nav-icon">${icon}</span><span>${label}</span>${id==="room"?`<b class="chat-unread-badge nav-chat-badge" data-chat-unread aria-label="${unread} tin nhắn chưa đọc" ${unread?"":"hidden"}>${unread>99?"99+":unread}</b>`:""}</button>`).join("")}</nav>
      <div class="sidebar-bottom"><div class="sidebar-account"><div class="avatar">${esc(initial(state.identity?.displayName))}</div><div class="sidebar-account-info"><strong>${esc(state.identity?.displayName||state.identity?.username)}</strong><small>${roleName(state.identity?.role)}</small></div><button class="sidebar-logout" data-action="logout" title="Đăng xuất" aria-label="Đăng xuất">↪</button></div><div class="server-health"><div><span class="health-dot"></span><strong>StudyFix kết nối</strong></div><small>TCP 5000 · Screen 5001 · Bài tập 5002 · Web 8080</small></div><div class="sidebar-copyright">© 2026 StudyFix · Học và thực hành lập trình</div></div>
    </aside><div class="mobile-nav-overlay" data-action="close-nav"></div>
    <div class="main-column"><button class="mobile-menu" data-action="toggle-nav" aria-label="Menu">☰</button><main class="workspace">${content}</main></div>
  </div>${callOverlay}`;
}

export function pageHeader(icon, title, subtitle, actions = "") {
  return `<div class="page-header"><div class="page-title-row"><div class="page-icon">${icon}</div><div><h2>${title}</h2><p>${subtitle}</p></div></div><div class="header-actions">${actions}</div></div>`;
}

export function renderLogin() {
  return `<div class="login-shell"><section class="login-visual"><div class="brand"><div class="brand-mark large">&lt;/&gt;</div><div><h1>StudyFix</h1><small>Học cùng nhau · Tiến bộ mỗi ngày</small></div></div><div class="login-hero"><div class="eyebrow">CODING · COLLABORATION · PROGRESS</div><h2>Học lập trình.<br/>Cùng nhau tiến bộ.</h2><p>Không gian học lập trình trực tuyến dành cho giáo viên và học sinh — từ luyện tập, chấm bài tự động đến cộng tác sửa lỗi và chia sẻ màn hình.</p><div class="hero-mini-grid"><div class="hero-mini"><strong>01</strong><span>Bài tập & chấm tự động</span></div><div class="hero-mini"><strong>02</strong><span>Code cộng tác trực tiếp</span></div><div class="hero-mini"><strong>03</strong><span>Thi & bảng xếp hạng</span></div></div></div><div class="login-foot">StudyFix · Học tập thực hành cho lớp học lập trình</div></section>
    <section class="login-form-wrap"><form class="login-form" id="login-form"><div class="eyebrow" style="color:#1677f9">WELCOME BACK</div><h2>Đăng nhập</h2><p>Đăng nhập bằng tài khoản StudyFix được cấp bởi hệ thống.</p><div class="form-row"><label for="username">Mã sinh viên / tài khoản giảng viên</label><input id="username" class="field" name="username" autocomplete="username" placeholder="Nhập mã sinh viên hoặc tài khoản giảng viên" required maxlength="64" /></div><div class="form-row"><label for="password">Mật khẩu</label><input id="password" class="field" name="password" type="password" autocomplete="current-password" placeholder="Nhập mật khẩu" required maxlength="256" /></div><button class="btn btn-primary wide login-submit" type="submit">Đăng nhập vào StudyFix <span>→</span></button><div id="login-error" class="login-message hidden"></div><div class="notice-box" style="margin-top:22px">Máy chủ sử dụng tài khoản hiện có trong database StudyFix. Không có tài khoản demo giả được tạo tự động trên giao diện.</div></form></section></div>`;
}

export function renderOverview() {
  const room = state.room,
    teacher = state.identity.role === "TEACHER";
  const todo = state.assignments.filter((a) => a.status === "OPEN").length;
  return `${pageHeader("◫", teacher ? "Phòng học trực tuyến" : "Không gian học tập", `Chào ${esc(state.identity.displayName)} — sẵn sàng cho buổi học hôm nay?`, room ? `<span class="chip green"><span class="chip-dot"></span>Đang ở phòng ${esc(room.roomCode||room.roomId)}</span>` : `<span class="chip"><span class="chip-dot"></span>Đã đăng nhập</span>`)}
    <section class="grid stats-grid"><div class="card stat-card"><div class="stat-icon">▣</div><div><div class="stat-label">Phòng hiện tại</div><div class="stat-number">${room ? 1 : 0}</div><div class="stat-foot">${room ? `Mã ${esc(room.roomCode||room.roomId)}` : "Chưa tham gia phòng"}</div></div></div><div class="card stat-card"><div class="stat-icon teal">▤</div><div><div class="stat-label">Bài đang mở</div><div class="stat-number">${todo}</div><div class="stat-foot">${teacher ? "Bài tập được giao trong phòng" : "Bài được giao cho bạn"}</div></div></div><div class="card stat-card"><div class="stat-icon green">✓</div><div><div class="stat-label">Thành viên trực tuyến</div><div class="stat-number">${room ? room.members.length : 0}</div><div class="stat-foot">${room ? "Cập nhật từ server" : "Sẽ hiện khi vào phòng"}</div></div></div><div class="card stat-card"><div class="stat-icon purple">♜</div><div><div class="stat-label">Kỳ thi</div><div class="stat-number">${state.contests.length}</div><div class="stat-foot">${teacher ? "Quản lý kỳ thi của phòng" : "Kỳ thi trong phòng"}</div></div></div></section>
    <section class="grid dashboard-grid"><div class="card"><div class="card-head"><div><h3>Đi nhanh đến tác vụ</h3><p>Các chức năng chính được gom về một nơi</p></div></div><div class="card-body"><div class="quick-actions">${room ? `<button class="quick-action" data-action="navigate" data-page="room"><span class="qa-icon">⌘</span><span><strong>Phòng học & code cộng tác</strong><small>Chỉnh sửa code cùng phòng, chat, thành viên và chia sẻ màn hình.</small></span></button>` : ""}<button class="quick-action" data-action="navigate" data-page="exercises"><span class="qa-icon">▤</span><span><strong>Luyện code</strong><small>Tự chọn bài, luyện code và theo dõi từng phiên bản nộp.</small></span></button><button class="quick-action" data-action="navigate" data-page="contests"><span class="qa-icon">♜</span><span><strong>Thi & xếp hạng</strong><small>${teacher ? "Tạo kỳ thi từ các bài trong ngân hàng." : "Vào kỳ thi, nộp bài và xem kết quả của chính mình."}</small></span></button><button class="quick-action" data-action="refresh"><span class="qa-icon">⟳</span><span><strong>Đồng bộ trạng thái</strong><small>Lấy trạng thái phòng và dữ liệu mới nhất từ server.</small></span></button></div></div></div>
    <div class="card"><div class="card-head"><div><h3>${teacher?"Phòng học do tôi quản lý":"Lớp học đang mở"}</h3><p>Chọn lớp và gửi yêu cầu tham gia · ${state.roomsTotal||0} phòng</p></div></div><div class="card-body">${renderRoomListCompact()}</div></div></section>
    <div style="height:16px"></div><section class="card"><div class="card-head"><div><h3>${teacher ? "Bài giao gần đây" : "Bài tập được giao"}</h3><p>Danh sách bài hiện hành trong phòng</p></div><span class="push"></span>${btn("Xem tất cả →", "room-assignments", "btn-soft btn-sm", state.room?"":"disabled")}</div><div class="card-body">${room ? renderAssignmentRows(state.assignments.slice(0, 5), teacher) : `<div class="empty-slim">Vào phòng học để xem bài tập và kỳ thi.</div>`}</div></section>`;
}

export function renderRoomListCompact() {
  const current=state.room?.roomId;
  const list=(state.rooms||[]).map(r=>{
    const isCurrent=current===r.roomId;
    const pending=r.admissionStatus==="PENDING";
    const joinLabel=state.identity.role==="STUDENT"&&r.admissionStatus&&r.admissionStatus!=="APPROVED"?(pending?"Đang chờ duyệt":r.admissionStatus==="REJECTED"?"Xin vào lại":"Xin vào lớp"):"Vào";
    return `<div class="assignment-row"><div class="task-number">⌘</div><div class="assignment-row-main"><strong>Phòng ${esc(r.roomCode||r.roomId)}</strong><small>${r.memberCount} thành viên · v${r.version}${isCurrent?" · Đang tham gia phòng":""}</small></div><div class="room-row-actions">${isCurrent?btn("Vào phòng", "navigate", "btn-primary btn-sm", 'data-page="room"'):btn(joinLabel, "join-room", "btn-soft btn-sm", `data-room-id="${esc(r.roomCode||r.roomId)}" ${pending?'disabled aria-busy="true"':""} ${current?'disabled title="Rời phòng hiện tại trước khi vào phòng khác"':""}`)}${isCurrent?btn("Rời phòng","leave-room","btn-soft btn-sm",`data-room-id="${esc(r.roomId)}"`):""}${state.manualRoomDelete&&r.isMine&&state.identity.role==="TEACHER"?btn("Xóa","delete-room","btn-danger btn-sm",`data-room-id="${esc(r.roomId)}" aria-label="Xóa phòng ${esc(r.roomCode||r.roomId)}"`):""}</div></div>`;
  }).join("")||`<div class="empty-slim">${state.roomSearch?.trim()?"Không tìm thấy phòng phù hợp. Thử từ khóa khác.":"Chưa có phòng."}</div>`;
  const search=`<form id="room-search-form" class="room-code-form" role="search"><label for="room-search">Tìm kiếm phòng</label><div><input id="room-search" name="q" type="search" class="field" placeholder="Nhập mã phòng cần tìm…" maxlength="160" value="${esc(state.roomSearch||"")}" autocomplete="off" /><button class="btn btn-soft" type="submit">Tìm</button></div></form>`;
  const page=state.roomPage||1,pages=state.roomsTotalPages||1;
  const pagination=`<div class="room-pagination">${btn("← Trước", "rooms-page", "btn-soft btn-sm", `data-page="${page-1}" ${page<=1?"disabled":""}`)}<span>Trang ${page} / ${pages} · ${state.roomsTotal||0} phòng</span>${btn("Sau →", "rooms-page", "btn-soft btn-sm", `data-page="${page+1}" ${page>=pages?"disabled":""}`)}</div>`;
  const create=!current&&state.identity.role==="TEACHER"?`<form id="create-room-form" class="room-code-form"><label for="new-room-code">Tạo phòng bằng mã của bạn</label><div><input id="new-room-code" name="roomCode" class="field" placeholder="Ví dụ: Lớp Java A" value="${esc(state.newRoomCode||"")}" maxlength="160" required /><button class="btn btn-primary" type="submit" ${state.customRoomCodes?"":"disabled"}>＋ Tạo phòng</button></div>${!state.customRoomCodes?'<small>Khởi động lại Server Manager để sử dụng mã phòng tự chọn.</small>':'<small>Mã dài tối đa 80 ký tự. Phòng được giữ lại cho đến khi chủ phòng bấm Xóa.</small>'}</form>`:"";
  const join=!current?`<form id="join-room-form" class="room-code-form"><label for="overview-room-code">Tham gia bằng mã phòng</label><div><input id="overview-room-code" name="roomId" class="field" placeholder="Nhập đúng mã giảng viên cung cấp" value="${esc(state.joinRoomCode||"")}" required/><button class="btn btn-soft" type="submit">Tham gia</button></div></form>`:'<p class="subtle-text">Rời phòng hiện tại trước khi tạo hoặc tham gia phòng khác.</p>';
  return `${search}${state.roomsError?`<div class="notice-box warn">${esc(state.roomsError)}</div>`:""}${list}${pagination}${create}${join}`;
}
export function renderAssignmentRows(list, teacher) {
  if (!list?.length)
    return `<div class="empty-slim">${teacher ? "Chưa có bài nào được giao trong phòng." : "Chưa có bài được giao cho bạn."}</div>`;
  return list
    .map(
      (a, i) =>
        `<div class="assignment-row ${!teacher && a.acceptedByMe ? "accepted-row" : ""}"><div class="task-number">${!teacher && a.acceptedByMe ? "✓" : String(i + 1).padStart(2, "0")}</div><div class="assignment-row-main"><strong>${esc(a.exerciseTitle || a.exerciseId)}</strong><small>${esc(a.exerciseId)} · ${modeLabel(a.mode)} · ${a.scope === "ROOM" ? "Cả phòng" : "Cá nhân"} · ${a.deadlineAt ? `Hạn ${dateTime(a.deadlineAt)}` : "Không hạn"}</small></div>${!teacher && a.acceptedByMe ? pill("AC", "ac") : pill(a.status || "OPEN", a.status === "OPEN" ? "easy" : "neutral")}${btn(teacher ? "Xem" : a.acceptedByMe ? "Xem kết quả" : "Mở bài", "open-assignment", "btn-soft btn-sm", `data-assignment-id="${esc(a.id)}"`)}</div>`,
    )
    .join("");
}

export function renderModal() {
  return "";
}

export function render() {
  if (state.booting) {
    root.innerHTML =
      '<div class="boot"><span class="brand-mark">SF</span><span>Đang mở StudyFix…</span></div>';
    return;
  }
  const active = document.activeElement;
  const mobileNavOpen=document.querySelector(".sidebar")?.classList?.contains("open");
  const activeId = active?.id;
  const activeScroll = [active?.scrollTop || 0, active?.scrollLeft || 0];
  let selection = null;
  try {
    selection = [active.selectionStart, active.selectionEnd];
  } catch (_) {}
  if (!state.identity) {
    root.innerHTML = renderLogin();
    return;
  }
  if (state.page === "room" && !state.room) {state.page = "overview";state.roomTab = "code";}
  rememberView();
  let content = renderPage(state.page);
  const previousChat=document.querySelector("#chat-list");
  const chatScroll=previousChat?{top:previousChat.scrollTop,atBottom:previousChat.scrollHeight-previousChat.scrollTop-previousChat.clientHeight<=32}:null;
  const previousMedia = captureMediaElements();
  const scrollPositions=Array.from(document.querySelectorAll?.(".meeting-content, .meeting-code-grid, .meeting-drawer, .meeting-rail")||[]).map(el=>({selector:`.${el.classList[0]}`,top:el.scrollTop,left:el.scrollLeft}));
  root.innerHTML = layout(content) + renderModal();
  if(mobileNavOpen){document.querySelector(".sidebar")?.classList.add("open");document.querySelector(".mobile-nav-overlay")?.classList.add("visible");}
  restoreMediaElements(previousMedia);
  for(const saved of scrollPositions){const el=document.querySelector(saved.selector);if(el){el.scrollTop=saved.top;el.scrollLeft=saved.left;}}
  if (activeId) {
    const replacement = document.getElementById(activeId);
    if (replacement) {
      replacement.focus({ preventScroll: true });
      if (selection && typeof replacement.setSelectionRange === "function") {
        try {
          replacement.setSelectionRange(selection[0], selection[1]);
        } catch (_) {}
      }
      replacement.scrollTop=activeScroll[0];replacement.scrollLeft=activeScroll[1];
      if(replacement.matches?.("#classroom-master, #classroom-branch")&&replacement.previousElementSibling)replacement.previousElementSibling.scrollTop=activeScroll[0];
    }
  }
  bindVideoElements();
  const chat = document.querySelector("#chat-list");
  if (chat) chat.scrollTop = !chatScroll||chatScroll.atBottom?chat.scrollHeight:chatScroll.top;
  markChatReadIfVisible();updateChatBadges();
}
