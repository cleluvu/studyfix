import { esc } from "../../shared/format.js";
import { btn } from "../../ui/components.js";

const verdicts = {
  ACCEPTED: ["AC · Đúng", "green"], WRONG_ANSWER: ["WA · Sai kết quả", "red"],
  COMPILE_ERROR: ["CE · Lỗi biên dịch", "red"], RUNTIME_ERROR: ["RE · Lỗi khi chạy", "red"],
  TIME_LIMIT_EXCEEDED: ["TLE · Quá thời gian", "red"], MEMORY_LIMIT_EXCEEDED: ["MLE · Quá bộ nhớ", "red"],
  OUTPUT_LIMIT_EXCEEDED: ["OLE · Quá giới hạn đầu ra", "red"],
  JUDGE_ERROR: ["Lỗi dịch vụ chấm", "amber"], SANDBOX_UNAVAILABLE: ["Dịch vụ chấm chưa sẵn sàng", "amber"], INTERNAL_ERROR: ["Lỗi dịch vụ chấm", "amber"]
};
export function checkSummary(check) {
  if (!check?.id) return { text: "Chưa nộp bài", tone: "neutral" };
  if (["QUEUED", "RUNNING"].includes(check.status)) return { text: check.status === "RUNNING" ? "Đang chấm bài…" : "Đã nhận bài · Đang chờ chấm…", tone: "blue", pending: true };
  const [text, tone] = verdicts[check.result?.verdict || check.status] || ["Đã chấm bài", "amber"];
  return { text, tone };
}
function value(label, text) {
  return `<div class="classroom-test-value"><strong>${esc(label)}</strong><pre>${esc(text ?? "")}</pre></div>`;
}
export function checkDetails(check) {
  if (!check?.id) return '<p class="classroom-result-message">Sửa code rồi bấm <strong>Nộp bài</strong> để chạy testcase. Nút Lưu và tự động lưu chỉ giữ code, chưa chấm bài.</p>';
  const result = check.result || {}, summary = checkSummary(check);
  if (summary.pending) return '<p class="classroom-result-message">Server đã nhận bản nộp. Kết quả sẽ tự cập nhật sau khi chấm xong; bạn không cần bấm Nộp bài lần nữa.</p>';
  const rows = result.publicTests || [];
  return `${result.feedback ? `<pre class="classroom-result-feedback ${summary.tone === "green" ? "terminal-success" : ""}">${esc(result.feedback)}</pre>` : ""}
    ${result.total !== undefined ? `<p class="meeting-terminal-score">Đạt ${esc(result.passed)}/${esc(result.total)} testcase · ${esc(result.score)}/${esc(result.maxScore)} điểm${result.durationMs !== undefined ? ` · ${esc(result.durationMs)} ms` : ""}</p>` : ""}
    ${rows.length ? `<ol class="classroom-test-results">${rows.map((test, i) => {
      const label = `Test ${test.index || i + 1}${test.hidden ? " · Test ẩn" : ""} · ${test.passed ? "Đạt" : "Không đạt"}`;
      if (test.hidden) return `<li class="classroom-test-row ${test.passed ? "passed" : "failed"}"><strong>${esc(label)}</strong><p>Chỉ hiển thị trạng thái của testcase ẩn.</p></li>`;
      return `<li class="classroom-test-row ${test.passed ? "passed" : "failed"}"><details ${test.passed ? "" : "open"}><summary>${esc(label)}</summary>${test.message ? `<p>${esc(test.message)}</p>` : ""}${test.input !== undefined ? value("Đầu vào", test.input) : ""}${test.expectedOutput !== undefined ? value("Kết quả mong đợi", test.expectedOutput) : ""}${test.actualOutput !== undefined ? value("Kết quả của bạn", test.actualOutput) : ""}</details></li>`;
    }).join("")}</ol>` : ""}`;
}
export function renderClassroomResult(branch, draft) {
  const check = branch.latestCheck, summary = checkSummary(check);
  const stale = check?.id && (branch.resultStale || draft?.dirty);
  return `<section id="classroom-result" class="meeting-terminal meeting-submission-result" aria-label="Kết quả chấm bài" aria-live="polite" aria-atomic="false"><div class="meeting-terminal-head"><strong>Kết quả chấm bài</strong><span class="meeting-status ${summary.tone}"><i></i>${esc(summary.text)}</span>${check?.id ? `<small>Bản nộp v${esc(check.branchVersion ?? branch.version)}</small>${btn("Xem bản đã nộp", "classroom-check-detail", "btn-ghost btn-sm", `data-id="${esc(check.id)}"`)}` : ""}${btn("Cập nhật kết quả", "classroom-refresh", "btn-ghost btn-sm")}</div>${stale ? '<p class="classroom-result-stale">Kết quả này thuộc bản đã nộp trước khi bạn sửa code. Bấm Nộp bài để chấm bản mới.</p>' : ""}<div class="classroom-result-body">${checkDetails(check)}</div></section>`;
}