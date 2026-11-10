# Xin vào lớp và giảng viên duyệt

## Cách sử dụng

1. Đóng Server Manager cũ rồi chạy lại `server/run-server-manager.ps1`. Cần bật chế độ MySQL đang dùng; migration tự nâng cấp bảng hiện có, không xóa dữ liệu.
2. Hai trình duyệt Teacher/Student nhấn **Ctrl+Shift+R**.
3. Teacher tạo lớp đang mở. Student thấy lớp trong **Lớp học của tôi → Lớp học đang mở**, không cần được nhập MSSV trước.
4. Student bấm **Xin vào lớp**, hoặc nhập mã lớp và bấm Tham gia. Chưa được vào code/chat/media; nút chuyển **Đang chờ duyệt**.
5. Đúng Teacher chủ lớp nhận thông báo **Yêu cầu vào lớp** với MSSV/họ tên và nút **Chấp nhận / Từ chối**. Trong dashboard cũng có bảng **Yêu cầu vào lớp đang chờ** để xử lý sau khi thông báo tắt.
6. Chấp nhận: Student được ghi nhận là thành viên; tab Student đang chờ tự vào lớp qua luồng JOIN_ROOM hiện có. Từ chối: Student nhận thông báo, có thể bấm **Xin vào lại**.

Teacher đang trong cuộc họp vẫn nhận thông báo và có thể duyệt ngay trên thông báo. Nếu WebSocket gián đoạn, polling dự phòng tiếp tục tải yêu cầu và kết quả.

**Cập nhật 05/11/2026 — duyệt mỗi lần vào:** mọi lượt Student vào lớp đều cần Teacher chấp nhận, kể cả tài khoản đã là thành viên hoặc được thêm MSSV thủ công. Bấm **Rời cuộc họp** hoặc **Rời phòng** kết thúc lượt tham gia; quay lại phải xin duyệt mới. Danh sách thành viên, code, bài làm và lịch sử điểm danh vẫn giữ nguyên. Chuyển qua lại giữa các tab chức năng khi vẫn đang tham gia lớp không tạo lượt vào mới.

Quyền duyệt gắn với đúng phiên đăng nhập và chỉ được dùng một lần. Thiết bị thứ hai của cùng tài khoản không dùng được quyền của thiết bị thứ nhất. Đăng xuất/mất kết nối hết timeout và restart làm hết hiệu lực quyền duyệt cũ. Trạng thái chờ tự vào thuộc tab đang gửi yêu cầu; nếu đã tải lại trang nhưng vẫn cùng phiên đăng nhập và chưa dùng quyền được duyệt, bấm **Vào** để sử dụng lần duyệt đó.

Lớp đã đóng/xóa không xuất hiện trong danh sách khám phá và không tiếp nhận/duyệt yêu cầu mới. Đóng lớp hủy quyền duyệt/yêu cầu chưa dùng; khi mở lại phải xin duyệt mới. Không xóa lịch sử code, điểm danh hay bài nộp.

## Luồng và quyền

- `GET /api/rooms`: Student thấy metadata của các lớp đang mở và trạng thái yêu cầu của chính mình. Xem danh sách không cấp quyền vào lớp.
- `POST /api/rooms/current/command`, `action=JOIN_ROOM`: mỗi lượt Student tạo yêu cầu PENDING, trả HTTP 202 cùng `admission`. Gửi lặp khi đang chờ không tạo thêm yêu cầu/thông báo mới. Yêu cầu APPROVED được tiêu thụ khi core thêm đúng kết nối vào lớp; không thể tái sử dụng sau khi ra ngoài.
- `POST /api/admin`, `action=APPROVE_JOIN` hoặc `REJECT_JOIN`: gửi `classId`, `requestId`. Server kiểm tra Teacher chủ lớp, Student/phiên đang hoạt động, lớp đang mở và đúng yêu cầu còn PENDING. `requestId` công khai không phải session token và không có quyền đăng nhập.
- WebSocket hiện có: Teacher nhận `joinRequests` trong `ADMIN_UPDATE` và `PRESENCE`; Student nhận `ADMISSION_UPDATE` chỉ gồm trạng thái của tài khoản mình. Không gửi mật khẩu, session token hay IP trong thông báo duyệt.
- Core TCP gọi `ClassAdmission.enter()` để kiểm tra và tiêu thụ đúng quyền duyệt của phiên: chỉ có ENROLLED trong danh sách thành viên không đủ để `/join`. Không thể dùng phiên TCP khác để vượt quyền của web session đã được duyệt.

## Database và source

Tái sử dụng `class_members` cho tư cách thành viên lâu dài. Migration v4 thêm `class_admissions` liên kết với users/classes/sessions hiện có, khóa duy nhất `(class_id,session_id)`, lưu request_id và trạng thái PENDING/APPROVED/ENTERED/REJECTED/LEFT/CANCELLED. Xin lại cấp request_id mới; thông báo cũ không duyệt được yêu cầu mới. Các ENROLLED cũ giữ nguyên nhưng vẫn phải duyệt mỗi lượt vào. Yêu cầu PENDING của cơ chế cũ được đánh dấu REMOVED trong danh sách thành viên vì không thể suy đoán phiên xin vào cũ. Không tạo hệ thống user/auth/WebSocket mới.

- `server/src/studyfix/ClassAdmission.java`: ghi yêu cầu, duyệt, truy vấn danh sách chờ và trạng thái cá nhân.
- `MySqlManagement.java`, `db/mysql-schema.sql`: migration và khám phá lớp đang mở.
- `AdminManagement.java`, `WebGatewayServer.java`: tích hợp API, quyền và WebSocket.
- `ClientHandler.java`: tiếp tục chặn TCP chưa được duyệt.
- `web-client/static/features/admin/admin.js`, `admin-view.js`: thông báo và bảng duyệt.
- `web-client/static/features/room/room.js`, `core/polling.js`, `core/presence-socket.js`: Student gửi/chờ và tự vào; polling dự phòng.
- `ui/render.js`, `ui/toast.js`, `core/state.js`, `features/auth/auth.js`: nhãn nút, thông báo, trạng thái chờ và dọn thông báo khi đăng xuất.

## Kiểm thử

- Java/Ant hồi quy: `server/build/admission-ant.log`.
- MySQL thật, database/instance kiểm thử riêng: `ClassAdmissionChecks` trong `MySqlAdminIntegrationTest`; gồm duyệt đúng chủ lớp, chống gửi trùng/quyết định cũ, từ chối/xin lại, TCP không vượt quyền, WebSocket cá nhân và đóng lớp.
- Frontend mô phỏng: `web-client/tests/check_admission.mjs`, mở rộng `check_admin.mjs`; giữ các bài kiểm tra web cũ.
- Chrome với backend/MySQL thật: mở rộng `teacher_dashboard_browser.cjs` để thử Student khám phá → xin vào → Teacher từ chối → Student xin lại → Teacher chấp nhận → Student tự vào.

Kiểm thử tự động dùng localhost và dữ liệu tách biệt; cần thử tiếp trên hai máy LAN thật theo hướng dẫn MySQL/LAN.

Kiểm chứng cập nhật 05/11/2026: MySQL thật đạt **107 kiểm tra**, Chrome hai phiên đạt cả **rời cuộc họp → xin lại → chờ duyệt → vào lại**, 15 script frontend đạt (meeting navigation tăng lên 21 kiểm tra). Log mới: `server/build/reentry-mysql-final.log`, `reentry-frontend.log`, `reentry-ant.log`. Các kết quả ngày 04/11 bên dưới là mốc trước thay đổi duyệt mỗi lượt.

Kết quả đã chạy ngày 04/11/2026:

- Ant `integration-test`: **BUILD SUCCESSFUL**, 2 phút 27 giây (`server/build/admission-ant.log`).
- MySQL thật: **95 checks PASS**, bao gồm kiểm thử xin/duyệt lớp (`server/build/admission-mysql-browser-final.log`). MySQL80 vận hành vẫn Running, database vận hành không được dùng để test.
- Chrome thật: **discover → request → reject → retry → approve → auto-enter PASS**, dùng hai browser context Teacher/Student với backend và MySQL thật. Ảnh sau duyệt: `server/build/teacher-admin-browser-qa/student-admitted-meeting.png`.
- **15 script frontend đạt**, gồm script admission mới và các hồi quy cũ (`server/build/admission-frontend.log`). Các test DOM/WebRTC mô phỏng không thay thế kiểm thử media thật trên LAN.
