# Hợp đồng tích hợp Member 3 — trạng thái hiện tại

Tab Member 3 hiện được tạo trong `MainFrame` bằng `ExerciseModule`. UI này dùng `ClientModuleContextAdapter` để lấy session, room state và host; không tự mở/đọc lại TCP control socket. Module có `AssignmentConnection` riêng tới cổng 5002, mỗi request mở connection framed riêng nên không tranh chấp reader với `TcpControlConnection`.

## Server

Server có package mới `studyfix.assignment.*`; khởi chạy bằng `studyfix.assignment.AssignmentEnabledServerMain` (script `run-with-assignments.ps1`). `ant run` mặc định chỉ khởi chạy core server và screen relay, không mở assignment port. Core 5000 và screen 5001 giữ nguyên; assignment listener chạy ở 5002.

## Request contract

Mỗi request bao gồm `protocolVersion: 1`, `requestId`, `type`, `sessionId`, `roomId`, và fields tương ứng. Frame là 4 byte big-endian + UTF-8 JSON; tối đa 65,536 bytes. Server tra danh tính/membership từ `StudyFixGateway`, không tin `role` hoặc `userId` do client tự khai.

Các request chính: `PING`, `CATALOG_LIST`, `CATALOG_GET`, `ASSIGN_CREATE`, `ASSIGN_LIST`, `ASSIGN_GET`, `SUBMIT_CREATE`, `SUBMISSION_LIST`, `SUBMISSION_RESULT`, `CONTEST_CREATE`, `CONTEST_LIST`, `LEADERBOARD_GET`. Xem `README_MEMBER3_VI.md` bên server để biết chính sách bảo mật và test.

## Source modes

- `PERSONAL`: client gửi source riêng trong field `sourceCode`. Server lưu immutable snapshot của lần nộp.
- `SHARED_SNAPSHOT`: client gửi mode; server đọc snapshot `Room` trực tiếp từ gateway tại thời điểm nhận bài. Source đó vẫn được lưu bất biến trong submission.

## Lưu trữ và chấm

Module dùng database `data/studyfix-learning.db` riêng; không migration database lõi. Hidden test và reference solution chỉ được đọc ở server. Worker dùng Docker sandbox `--network none`; nếu Docker/image không sẵn sàng, trạng thái là `SANDBOX_UNAVAILABLE` và không chạy source ngoài sandbox.

## Quy tắc source baseline

Tất cả nghiệp vụ Member 3 được đặt trong package mới `studyfix.assignment` và `vn.studyfix.client.exercise`, kèm test mới. Để tab xuất hiện trong cửa sổ Swing, `MainFrame.java` có một hook tích hợp: tạo `ExerciseModule` thay cho placeholder; không thay code login, core networking, room/chat/code collaboration hoặc screen-share.
