// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { toastRegion } from "../core/dom.js";
import { esc } from "../shared/format.js";

export function toast(message, type = "info") {
  const node = document.createElement("div");
  node.className = `toast ${type === "error" ? "error" : type === "success" ? "success" : ""}`;
  const icon = type === "error" ? "✕" : type === "success" ? "✓" : "ℹ";
  node.innerHTML = `<strong>${icon}</strong><span>${esc(message)}</span>`;
  toastRegion.appendChild(node);
  setTimeout(() => node.remove(), 4600);
}
export function notifyChatMessage({name, text, count = 1, roomId}) {
  const existing = Array.from(toastRegion.querySelectorAll?.(".chat-toast") || []);
  while(existing.length >= 3) existing.shift().remove();
  const node = document.createElement("div");
  node.className = "toast chat-toast";
  node.setAttribute("role", "status");
  node.innerHTML = `<div class="chat-toast-icon" aria-hidden="true">✉</div><div class="chat-toast-body"><strong>Tin nhắn mới${count>1?` (${count})`:""} · Phòng ${esc(roomId)}</strong><div class="chat-toast-sender">${esc(name)}</div><p>${esc(text)}</p><button class="chat-toast-open" data-action="chat-open">Mở chat →</button></div><button class="chat-toast-close" data-action="chat-dismiss-toast" aria-label="Đóng thông báo">×</button>`;
  toastRegion.appendChild(node);
  setTimeout(() => node.remove(), 8000);
}

export function fail(error) {
  toast(error?.message || String(error), "error");
}

export function notifyJoinRequest(request){
  const node=document.createElement("div");node.className="toast join-request-toast";node.setAttribute("role","status");
  node.setAttribute("data-request-key",request.requestId||`${request.classId}:${request.userId}:${request.requestedAt}`);
  const attrs=`data-class-id="${esc(request.classId)}" data-id="${esc(request.userId)}" data-requested-at="${Number(request.requestedAt)}" data-request-id="${esc(request.requestId)}"`;
  node.innerHTML=`<div><strong>Yêu cầu vào lớp ${esc(request.roomCode)}</strong><p>${esc(request.displayName)} · ${esc(request.studentCode)}</p><button class="btn btn-primary btn-sm" data-action="admin-approve-join" ${attrs}>Chấp nhận</button> <button class="btn btn-soft btn-sm" data-action="admin-reject-join" ${attrs}>Từ chối</button></div>`;
  toastRegion.appendChild(node);setTimeout(()=>node.remove(),12000);
}
export function clearJoinRequestNotifications(){for(const node of document.querySelectorAll?.(".join-request-toast")||[])node.remove();}

export function busy(button, on) {
  if (button) {
    button.disabled = on;
    if (on) button.dataset.oldLabel = button.innerHTML;
    button.innerHTML = on
      ? "Đang xử lý…"
      : button.dataset.oldLabel || button.innerHTML;
  }
}
