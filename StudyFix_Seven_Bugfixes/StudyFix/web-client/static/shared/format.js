// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

export const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (ch) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        ch
      ],
  );

export const dateTime = (value) => {
  if (!value || Number(value) === 0) return "Không đặt hạn";
  const d = new Date(Number(value));
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
};

export const timeOnly = (value) =>
  new Date(Number(value || Date.now())).toLocaleTimeString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
  });

export const ago = (value) => {
  const sec = Math.max(
    0,
    Math.round((Date.now() - Number(value || Date.now())) / 1000),
  );
  return sec < 60
    ? `${sec} giây trước`
    : sec < 3600
      ? `${Math.floor(sec / 60)} phút trước`
      : dateTime(value);
};

export const initial = (name) =>
  String(name || "?")
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((v) => v[0] || "")
    .join("")
    .toUpperCase() || "?";

export const roleName = (role) => (role === "TEACHER" ? "Giáo viên" : "Học sinh");

export const modeLabel = (mode) =>
  mode === "DEBUG_COLLAB"
    ? "Sửa lỗi cùng phòng"
    : mode === "BUILD_FROM_SCRATCH"
      ? "Lập trình từ đầu"
      : mode || "—";

export const diffClass = (value) =>
  ({
    Dễ: "easy",
    "Trung bình": "medium",
    Khó: "hard",
    EASY: "easy",
    MEDIUM: "medium",
    HARD: "hard",
  })[value] || "neutral";

export const modeClass = (mode) => (mode === "DEBUG_COLLAB" ? "debug" : "scratch");

export const humanVerdict = (value) =>
  ({
    ACCEPTED: "AC",
    AC: "AC",
    PASS: "PASS",
    FAILED: "FAILED",
    WRONG_ANSWER: "Sai đáp án",
    COMPILATION_ERROR: "Lỗi biên dịch",
    COMPILE_ERROR: "Lỗi biên dịch",
    MEMORY_LIMIT_EXCEEDED: "Quá bộ nhớ",
    OUTPUT_LIMIT_EXCEEDED: "Quá giới hạn đầu ra",
    RUNTIME_ERROR: "Lỗi thực thi",
    TIME_LIMIT_EXCEEDED: "Quá thời gian",
    QUEUED: "Đang chờ",
    RUNNING: "Đang chấm",
    SANDBOX_UNAVAILABLE: "Sandbox chưa sẵn sàng",
    INTERNAL_ERROR: "Lỗi hệ thống",
    NOT_SUBMITTED: "Chưa nộp",
  })[String(value || "").toUpperCase()] ||
  value ||
  "—";

export const statusClass = (value) =>
  ["ACCEPTED", "AC", "PASS"].includes(String(value || "").toUpperCase())
    ? "ac"
    : [
          "FAILED",
          "WRONG_ANSWER",
          "COMPILATION_ERROR",
          "COMPILE_ERROR",
          "MEMORY_LIMIT_EXCEEDED",
          "OUTPUT_LIMIT_EXCEEDED",
          "RUNTIME_ERROR",
          "TIME_LIMIT_EXCEEDED",
        ].includes(String(value || "").toUpperCase())
      ? "failed"
      : ["QUEUED","RUNNING"].includes(String(value||"").toUpperCase())?"medium":"neutral";

export function nowMs() {
  return Date.now();
}
