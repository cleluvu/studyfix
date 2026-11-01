// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

import { state } from "../core/state.js";
import { root, bindVideoElements, captureMediaElements, restoreMediaElements } from "../core/dom.js";
import { renderPage } from "../core/router.js";
import { esc, dateTime, initial, roleName, modeLabel } from "../shared/format.js";
import { btn, pill } from "./components.js";

export function layout(content) {
  if (state.page === "room" && state.room) return content;
  const callOverlay=state.callJoined?`<aside class="floating-call"><strong>Cuộc gọi đang bật</strong><button data-action="navigate" data-page="room">Về phòng</button><button data-action="call-mic">${state.callLocal?.getAudioTracks().some(t=>t.enabled)?"Tắt mic":"Bật mic"}</button><button data-action="call-camera">${state.callLocal?.getVideoTracks().length?"Tắt camera":"Bật camera"}</button><button data-action="call-leave">Ngắt gọi</button></aside><div class="meeting-audio-bank">${(state.callRoster||[]).filter(p=>p.peerId!==state.peerId).map(p=>`<audio data-call-audio="${esc(p.peerId)}" autoplay></audio>`).join("")}</div>`:"";
  const nav = [
    ["overview", "⌂", "Tổng quan"],
    ["room", "▣", "Phòng học"],
    ["exercises", "▤", "Bài tập"],
    ["contests", "♜", "Thi & xếp hạng"],
  ];
  return `<div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand"><div class="brand-mark">&lt;/&gt;</div><div><h1>StudyFix</h1><p>Học cùng nhau · Tiến bộ mỗi ngày</p></div></div>
      <div class="side-label">Không gian học tập</div>
      <nav class="nav">${nav.map(([id, icon, label]) => `<button class="nav-btn ${state.page === id ? "active" : ""}" data-action="navigate" data-page="${id}"><span class="nav-icon">${icon}</span><span>${label}</span></button>`).join("")}</nav>
      <div class="sidebar-bottom"><div class="server-health"><div><span class="health-dot"></span><strong>StudyFix kết nối</strong></div><small>TCP 5000 · Screen 5001 · Bài tập 5002 · Web 8080</small></div><div class="sidebar-copyright">© 2026 StudyFix · Học và thực hành lập trình</div></div>
    </aside><div class="mobile-nav-overlay" data-action="close-nav"></div>
    <div class="main-column"><header class="topbar"><button class="mobile-menu" data-action="toggle-nav">☰</button><span class="crumb">${{ overview: "Tổng quan", room: "Phòng học", exercises: "Bài tập", contests: "Thi & xếp hạng" }[state.page] || "StudyFix"}</span><div class="global-search"><span class="search-icon">⌕</span><input id="global-search" class="field" placeholder="Tìm kiếm phòng, học sinh, bài tập…" value="${esc(state.globalQuery || "")}" /></div><div class="topbar-spacer"></div><button class="icon-btn" title="Làm mới" data-action="refresh">⟳</button><div class="profile-menu"><div class="avatar">${esc(initial(state.identity?.displayName))}</div><div><strong>${esc(state.identity?.displayName || state.identity?.username)}</strong><small>${roleName(state.identity?.role)}</small></div><button class="btn btn-ghost btn-sm" data-action="logout">⌄</button></div></header><main class="workspace">${content}</main></div>
  </div>${callOverlay}`;
}

export function pageHeader(icon, title, subtitle, actions = "") {
  return `<div class="page-header"><div class="page-title-row"><div class="page-icon">${icon}</div><div><h2>${title}</h2><p>${subtitle}</p></div></div><div class="header-actions">${actions}</div></div>`;
}

export function renderLogin() {
  return `<div class="login-shell"><section class="login-visual"><div class="brand"><div class="brand-mark large">&lt;/&gt;</div><div><h1>StudyFix</h1><small>Học cùng nhau · Tiến bộ mỗi ngày</small></div></div><div class="login-hero"><div class="eyebrow">CODING · COLLABORATION · PROGRESS</div><h2>Học lập trình.<br/>Cùng nhau tiến bộ.</h2><p>Không gian học lập trình trực tuyến dành cho giáo viên và học sinh — từ luyện tập, chấm bài tự động đến cộng tác sửa lỗi và chia sẻ màn hình.</p><div class="hero-mini-grid"><div class="hero-mini"><strong>01</strong><span>Bài tập & chấm tự động</span></div><div class="hero-mini"><strong>02</strong><span>Code cộng tác trực tiếp</span></div><div class="hero-mini"><strong>03</strong><span>Thi & bảng xếp hạng</span></div></div></div><div class="login-foot">StudyFix · Học tập thực hành cho lớp học lập trình</div></section>
    <section class="login-form-wrap"><form class="login-form" id="login-form"><div class="eyebrow" style="color:#1677f9">WELCOME BACK</div><h2>Đăng nhập</h2><p>Đăng nhập bằng tài khoản StudyFix được cấp bởi hệ thống.</p><div class="form-row"><label for="username">Tên đăng nhập</label><input id="username" class="field" name="username" autocomplete="username" placeholder="Nhập tên đăng nhập" required maxlength="64" /></div><div class="form-row"><label for="password">Mật khẩu</label><input id="password" class="field" name="password" type="password" autocomplete="current-password" placeholder="Nhập mật khẩu" required maxlength="256" /></div><button class="btn btn-primary wide login-submit" type="submit">Đăng nhập vào StudyFix <span>→</span></button><div id="login-error" class="login-message hidden"></div><div class="notice-box" style="margin-top:22px">Máy chủ sử dụng tài khoản hiện có trong database StudyFix. Không có tài khoản demo giả được tạo tự động trên giao diện.</div></form></section></div>`;
}

export function renderOverview() {
  const room = state.room,
    teacher = state.identity.role === "TEACHER";
  const todo = state.assignments.filter((a) => a.status === "OPEN").length;
  return `${pageHeader("◫", "Tổng quan", `Chào ${esc(state.identity.displayName)} — sẵn sàng cho buổi học hôm nay?`, room ? `<span class="chip green"><span class="chip-dot"></span>Đang ở phòng ${esc(room.roomId)}</span>` : `<span class="chip"><span class="chip-dot"></span>Đã đăng nhập</span>`)}
    <section class="grid stats-grid"><div class="card stat-card"><div class="stat-icon">▣</div><div><div class="stat-label">Phòng hiện tại</div><div class="stat-number">${room ? 1 : 0}</div><div class="stat-foot">${room ? `Mã ${esc(room.roomId)}` : "Chưa tham gia phòng"}</div></div></div><div class="card stat-card"><div class="stat-icon teal">▤</div><div><div class="stat-label">Bài đang mở</div><div class="stat-number">${todo}</div><div class="stat-foot">${teacher ? "Bài tập được giao trong phòng" : "Bài được giao cho bạn"}</div></div></div><div class="card stat-card"><div class="stat-icon green">✓</div><div><div class="stat-label">Thành viên trực tuyến</div><div class="stat-number">${room ? room.members.length : 0}</div><div class="stat-foot">${room ? "Cập nhật từ server" : "Sẽ hiện khi vào phòng"}</div></div></div><div class="card stat-card"><div class="stat-icon purple">♜</div><div><div class="stat-label">Kỳ thi</div><div class="stat-number">${state.contests.length}</div><div class="stat-foot">${teacher ? "Quản lý kỳ thi của phòng" : "Kỳ thi trong phòng"}</div></div></div></section>
    <section class="grid dashboard-grid"><div class="card"><div class="card-head"><div><h3>Đi nhanh đến tác vụ</h3><p>Các chức năng chính được gom về một nơi</p></div></div><div class="card-body"><div class="quick-actions"><button class="quick-action" data-action="navigate" data-page="room"><span class="qa-icon">⌘</span><span><strong>Phòng học & code cộng tác</strong><small>Chỉnh sửa code cùng phòng, chat, thành viên và chia sẻ màn hình.</small></span></button><button class="quick-action" data-action="navigate" data-page="exercises"><span class="qa-icon">▤</span><span><strong>${teacher ? "Ngân hàng & giao bài" : "Bài được giao"}</strong><small>${teacher ? "Chọn bài, đặt phạm vi và hạn nộp ngay trên danh sách." : "Mở đề, viết code, nộp bài và xem kết quả chấm."}</small></span></button><button class="quick-action" data-action="navigate" data-page="contests"><span class="qa-icon">♜</span><span><strong>Thi & xếp hạng</strong><small>${teacher ? "Tạo kỳ thi từ các bài trong ngân hàng." : "Vào kỳ thi, nộp bài và xem kết quả của chính mình."}</small></span></button><button class="quick-action" data-action="refresh"><span class="qa-icon">⟳</span><span><strong>Đồng bộ trạng thái</strong><small>Lấy trạng thái phòng và dữ liệu mới nhất từ server.</small></span></button></div></div></div>
    <div class="card"><div class="card-head"><div><h3>${teacher ? "Phòng học của hệ thống" : "Vào phòng học"}</h3><p>${teacher ? "Các phòng hiện có trong hệ thống" : "Nhập mã phòng do giáo viên cung cấp"}</p></div><span class="push"></span></div><div class="card-body">${room ? `<div class="assignment-row"><div class="task-number">⌘</div><div class="assignment-row-main"><strong>Phòng ${esc(room.roomId)}</strong><small>${room.members.length} thành viên · version ${room.version}</small></div>${btn("Mở phòng →", "navigate", "btn-primary", 'data-page="room"')}</div>` : teacher ? renderRoomListCompact() : `<div class="form-row"><label>Mã phòng</label><input class="field" id="overview-room-code" placeholder="Ví dụ: LTM-01" /></div><button class="btn btn-primary wide" data-action="join-room-overview">Tham gia phòng →</button>`}</div></div></section>
    <div style="height:16px"></div><section class="card"><div class="card-head"><div><h3>${teacher ? "Bài giao gần đây" : "Bài tập được giao"}</h3><p>Danh sách bài hiện hành trong phòng</p></div><span class="push"></span>${btn("Xem tất cả →", "navigate", "btn-soft btn-sm", 'data-page="exercises"')}</div><div class="card-body">${room ? renderAssignmentRows(state.assignments.slice(0, 5), teacher) : `<div class="empty-slim">Vào phòng học để xem bài tập và kỳ thi.</div>`}</div></section>`;
}

export function renderRoomListCompact() {
  if (!state.rooms.length)
    return `<div class="empty-slim">Chưa có phòng. Tạo phòng mới để bắt đầu buổi học.</div><button class="btn btn-primary wide" data-action="create-room">＋ Tạo phòng học</button>`;
  return `<div>${state.rooms
    .slice(0, 4)
    .map(
      (r) =>
        `<div class="assignment-row"><div class="task-number">⌘</div><div class="assignment-row-main"><strong>Phòng ${esc(r.roomId)}</strong><small>${r.memberCount} thành viên · v${r.version}</small></div>${btn("Vào", "join-room", "btn-soft btn-sm", `data-room-id="${esc(r.roomId)}"`)}</div>`,
    )
    .join(
      "",
    )}</div><button class="btn btn-primary wide" style="margin-top:12px" data-action="create-room">＋ Tạo phòng mới</button>`;
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
  let content = renderPage(state.page);
  const previousMedia = captureMediaElements();
  const scrollPositions=Array.from(document.querySelectorAll?.(".meeting-content, .meeting-code-grid, .meeting-drawer, .meeting-rail")||[]).map(el=>({selector:`.${el.classList[0]}`,top:el.scrollTop,left:el.scrollLeft}));
  root.innerHTML = layout(content) + renderModal();
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
  if (chat) chat.scrollTop = chat.scrollHeight;
}
