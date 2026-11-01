// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

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

export function busy(button, on) {
  if (button) {
    button.disabled = on;
    if (on) button.dataset.oldLabel = button.innerHTML;
    button.innerHTML = on
      ? "Đang xử lý…"
      : button.dataset.oldLabel || button.innerHTML;
  }
}
