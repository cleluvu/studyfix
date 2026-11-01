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
