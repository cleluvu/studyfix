// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state } from "./state.js";

export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
  if (options.body !== undefined)
    headers.set("Content-Type", "application/json");
  let response;
  try {
    response = await fetch(path, {
      ...options,
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch (_) {
    throw new Error(
      "Không kết nối được StudyFix Web Gateway. Hãy kiểm tra Server Manager và cổng 8080.",
    );
  }
  let payload = {};
  try {
    payload = await response.json();
  } catch (_) {}
  if (response.status === 401 && state.token) {
    // Phát sự kiện thay vì import auth.js trực tiếp để tránh vòng phụ thuộc.
    // auth.js nhận sự kiện này rồi thực hiện đăng xuất.
    document.dispatchEvent(new CustomEvent("studyfix:unauthorized"));
    throw new Error(payload.message || "Phiên đăng nhập hết hạn.");
  }
  if (!response.ok || payload.ok === false) {
    const error = new Error(
      payload.message || `Yêu cầu thất bại (${response.status}).`,
    );
    error.status = response.status;
    error.code = payload.error || payload.errorCode || "";
    throw error;
  }
  return payload;
}

export async function assignment(type, data = {}) {
  const result = await api("/api/assignments", {
    method: "POST",
    body: { type, ...data },
  });
  if (result.status !== "OK"){
    const error=new Error(result.message || "Nghiệp vụ bài tập thất bại.");error.code=result.errorCode||"";throw error;
  }
  return result.data || {};
}

export async function command(action, data = {}) {
  return api("/api/rooms/current/command", {
    method: "POST",
    body: { action, ...data },
  });
}
