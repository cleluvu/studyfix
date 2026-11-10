// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { esc } from "../shared/format.js";

export const pill = (text, cls = "neutral") =>
  `<span class="pill ${cls}">${esc(text)}</span>`;

export const btn = (label, action, cls = "btn-soft", attrs = "") =>
  `<button class="btn ${cls}" data-action="${esc(action)}" ${attrs}>${label}</button>`;

export const empty = (icon, title, desc, actionLabel = "", action = "") =>
  `<div class="empty"><div class="empty-icon">${icon}</div><strong>${esc(title)}</strong><div>${esc(desc)}</div>${actionLabel ? `<div style="margin-top:15px">${btn(esc(actionLabel), action, "btn-primary")}</div>` : ""}</div>`;
