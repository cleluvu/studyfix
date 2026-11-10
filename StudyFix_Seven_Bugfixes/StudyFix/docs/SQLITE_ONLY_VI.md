# StudyFix chỉ sử dụng SQLite

Ngày thực hiện: 09/11/2026.

## Phạm vi

Backend Java, HTTP/TCP/WebSocket và giao diện web giữ nguyên. Toàn bộ dữ liệu ứng dụng dùng SQLite; không cần cài, kết nối hay bật dịch vụ MySQL.

- Quản trị: `server/data/studyfix-management.db`.
- Lõi phòng học/code: `server/data/studyfix.db`.
- Bài tập, đề thi và kết quả: `server/data/studyfix-learning.db`.

Giữ ba file hiện có để bảo toàn ID, mật khẩu đã băm và liên kết dữ liệu. Không gộp hay tạo lại database.

## Thay đổi

- `ManagementConfig` chỉ chấp nhận SQLite. Cấu hình chọn engine khác bị từ chối.
- `ManagementDatabase`, `TeacherRepository`, `ClassAdmission` dùng cú pháp SQLite trực tiếp, không chuyển đổi dialect lúc chạy.
- Gỡ nhánh migration MySQL, schema MySQL, CLI sao chép MySQL, công cụ test MySQL và MySQL Connector/J khỏi source, thư viện và classpath.
- Thay thông báo MySQL ở API/giao diện bằng SQLite; lỗi ràng buộc SQLite trả HTTP 409 `DATA_CONFLICT`, kiểm tra rollback bằng trigger SQLite thật.
- Quản trị dùng khóa ngoại, WAL, busy timeout 8 giây và transaction IMMEDIATE; không thay thế khóa hàng bằng xử lý ngoài transaction.
- Launcher chọn SQLite và trỏ đúng database quản trị hiện có; mặc định không có `.env` cũng dùng `data/studyfix-management.db`.
- Bộ kiểm thử quản trị chạy trong thư mục SQLite tạm riêng, kiểm tra ownership, tạo/import tài khoản, rollback, duyệt vào lớp, WebSocket, điểm danh, khóa/reset tài khoản và restart.

Các tài liệu/log/báo cáo cũ nhắc đến MySQL chỉ là lịch sử; không phải dependency của hệ thống hiện tại.

## Chạy

Cần JDK 26 và Apache Ant. Từ thư mục `StudyFix`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-with-assignments.ps1
```

Mở `http://localhost:8080`. Các cổng: 5000 (lõi), 5001 (screen relay), 5002 (bài tập), 8080 (web), 8081 (WebSocket). Dừng bằng Ctrl+C. Không chạy đồng thời hai server trên cùng database/cổng.

Docker chỉ cần cho chạy/chấm mã Java trong sandbox. Không cần Docker để đọc dữ liệu SQLite hay mở giao diện.

## Cấu hình

```ini
STUDYFIX_MANAGEMENT_DB=sqlite
STUDYFIX_SQLITE_MANAGEMENT_PATH=data/studyfix-management.db
```

`STUDYFIX_SQLITE_IMPORT_CORE=true` chỉ dùng khi chủ động nhập dữ liệu từ lõi SQLite cũ sang quản trị SQLite. Không bật để khởi động thông thường. Không nhập lại tài khoản từ bản sao lõi để vượt qua trạng thái khóa hoặc thay mật khẩu của database quản trị.

Tạo Teacher mới bằng `server/run-management-cli.ps1` trong terminal tương tác; mật khẩu nhập ẩn. Không reset mật khẩu người dùng hiện có trong quá trình chuyển đổi.

## Sao lưu và khôi phục

Bản sao trước thay đổi: `server/backups/before-sqlite-only-20261109-212328/`. Cả ba file đã được sao lưu bằng SQLite Backup API; `inventory.json` ghi số dòng, integrity và khóa ngoại trước thay đổi. Cả ba database ban đầu có integrity `ok`, không có lỗi khóa ngoại; lõi và quản trị cùng có 8 tài khoản (2 Teacher, 6 Student).

Để khôi phục, dừng server trước, sao lưu trạng thái hiện tại rồi phục hồi đồng bộ cả ba database từ cùng một bản sao. Không sao chép riêng file `.db` trong lúc server đang ghi WAL; dùng SQLite Backup API hoặc dừng server hoàn toàn trước khi sao chép.

## Kiểm thử

```powershell
cd server
ant -Dbuild.dir=build-sqlite-only integration-test exam-bank-test
```

Kết quả trên máy này:

- 21/21 bộ kiểm tra frontend đạt; [kết quả frontend](../server/reports/SQLITE_ONLY_FRONTEND_TESTS.json).
- Bộ quản trị toàn phần đạt 124 kiểm tra trên SQLite thật. Bộ SQLite quản trị cơ bản đạt, gồm lỗi ràng buộc HTTP 409 và rollback.
- Đã kiểm chứng các nhóm trong `integration-test` và `exam-bank-test`: phòng học/chat/phòng, practice, server monitor/reliability, LAN/WebSocket, exam, Week/ngân hàng bài, assignment TCP, edit/recovery/history, HTTP, screen relay, server admin và console.
- Các nhóm đã đạt được giữ lại từ `server/build-sqlite-only-final.log`. Phần còn lại chạy bằng target sinh từ nội dung `integration-test` trong `server/build-sqlite-only/verification.xml`, đạt `BUILD SUCCESSFUL` ở `server/build-sqlite-only-remaining.log`.
- Một kiểm tra dừng PBKDF có kiểm soát báo SKIP vì Oracle JDK từ chối provider JCE thử nghiệm không ký. Xác thực thật và xóa mật khẩu khỏi bộ nhớ vẫn được kiểm tra. Không thay đổi chính sách bảo mật của JVM.
- Đã sửa test bất đồng bộ để đợi log worker xuất hiện trước khi kiểm tra; không thay đổi xử lý nghiệp vụ cho mục đích test.
- Docker Engine chưa chạy nên chưa tái kiểm chứng chấm mã thật trong container. Các kiểm tra persistence dùng fixture/test double được ghi rõ trong log.

Bản chính đang chạy nền tại **http://localhost:8080**. `/api/health` trả `ok=true`, `managementDatabase=sqlite`; WebSocket bật ở 8081. HTML trả HTTP 200. Log bản chạy nằm ở `server/logs/sqlite-only-server.stdout.log` và `server/logs/sqlite-only-server.stderr.log`.

[Báo cáo kiểm chứng](../server/reports/SQLITE_ONLY_AUDIT.json) xác nhận cả ba database toàn vẹn, không có lỗi khóa ngoại, giữ nguyên 8 tài khoản và password hash; JAR/manifest/classpath không còn MySQL. Source/cấu hình/test đang dùng không còn tham chiếu engine cũ.
