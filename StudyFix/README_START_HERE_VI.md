# StudyFix — Web FE + Java Gateway

**Giao diện cuộc họp:** Phòng học có giao diện tối cho giảng viên/sinh viên, lưới code, terminal và gọi mic/camera WebRTC trong LAN. Xem [hướng dẫn cuộc họp](docs/MEETING_UI_VI.md). Khởi động lại Server Manager để nạp backend mới, rồi tải lại trình duyệt.

**Cập nhật 29/10/2026:** Phòng học web dùng **Master → nhánh riêng → theo dõi → Merge**, giữ Java/TCP/SQLite. Xem [hướng dẫn phòng học mới](docs/CLASSROOM_BRANCH_WORKFLOW_VI.md) để chạy và dùng. Đây là tài liệu hiện hành cho phòng học; các báo cáo bàn giao bên dưới mô tả những phiên bản trước.

Chạy từ thư mục `StudyFix`: `powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1`, rồi mở `http://localhost:8080`. Sao lưu `server/data/` trước lần chạy bản mới đầu tiên.

1. Đọc `RUN_IN_VSCODE_VI.md` để thiết lập JDK 26/Ant và khởi động Server Manager.
2. Đọc `docs/StudyFix_UI_UX_Redesign_Plan.md` để xem yêu cầu gốc.
3. Đọc `docs/IMPLEMENTATION_STATUS_VI.md` để xem hạng mục đã có, kết quả kiểm thử và giới hạn còn mở.
4. Sao lưu toàn bộ `server/data/` trước lần chạy đầu tiên; không xóa database để xử lý lỗi.

Mở PowerShell tại thư mục project, chạy `server\run-server-manager.ps1`, rồi mở `http://localhost:8080`. Yêu cầu JDK 26 + Apache Ant. Docker Engine phải hoạt động và có image chấm bài để nghiệm thu chấm code thật.
