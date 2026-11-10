# Nhận source StudyFix để sửa code

Gói này chứa source Java server, frontend web, thư viện JDBC/WebSocket, cấu hình build, test và tài liệu. Đây là bản source hiện tại, không kèm database người dùng, mật khẩu cấu hình thật hoặc chứng chỉ HTTPS của máy gửi.

## Mở và chỉnh sửa

1. Giải nén ZIP ra một thư mục riêng, ví dụ `D:\Projects\StudyFix_Source`. Không mở trực tiếp file bên trong ZIP.
2. Với VS Code: Open Folder, chọn thư mục `StudyFix` sau khi giải nén.
3. Với NetBeans: Open Project, chọn `StudyFix/server` (project Java Ant).
4. Backend nằm trong `StudyFix/server/src`; giao diện nằm trong `StudyFix/web-client/static`; test nằm trong `server/test` và `web-client/tests`.

Giao diện web dùng JavaScript thuần, không cần `npm install` để chạy ứng dụng. Máy nhận vẫn cần cài JDK 26 và Apache Ant/Ant đi kèm NetBeans. Thiết lập `JAVA_HOME` tới JDK và `ANT_HOME` tới Ant hoặc thêm các lệnh vào PATH. Kiểm tra `java -version`, `javac -version`, `ant -version` trước khi chạy.

## Cấu hình trên máy mới

1. Sao chép `StudyFix/server/.env.example` thành `StudyFix/server/.env`.
2. Nếu dùng chức năng quản lý lớp và theo dõi lớp học hiện tại, cài/bật MySQL Server. Dùng tài khoản quản trị MySQL chạy `server/db/mysql/00_create_database.sql` và tạo tài khoản ứng dụng riêng có quyền trên database `studyfix_management`.
3. Điền `STUDYFIX_MYSQL_URL`, `STUDYFIX_MYSQL_USER`, `STUDYFIX_MYSQL_PASSWORD` trong `.env` theo máy mới. Không dùng mật khẩu của máy gửi.
4. Vì gói không mang database cũ, đặt `STUDYFIX_MYSQL_IMPORT_SQLITE=false`.
5. Đặt `STUDYFIX_BOOTSTRAP_TEACHER` (tên tài khoản tự chọn), `STUDYFIX_BOOTSTRAP_PASSWORD` (mật khẩu tự chọn ít nhất 12 ký tự), `STUDYFIX_BOOTSTRAP_NAME`. Đây là tài khoản giáo viên ban đầu cho database mới. Không có sẵn tài khoản đăng nhập thật trong gói source.
6. Bảng dữ liệu được khởi tạo khi server chạy. Tài khoản sinh viên, lớp và dữ liệu bài làm cũ của máy gửi không có trong gói này; tạo dữ liệu phát triển riêng trên máy nhận.

Xem thêm `StudyFix/docs/MYSQL_ADMIN_WEBSOCKET_VI.md`. Nếu chỉ cần nghiên cứu code, có thể mở source ngay mà chưa cần cấu hình MySQL.

## Chạy

Mở PowerShell tại thư mục có thư mục con `StudyFix`, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
```

Script build lại JAR và mở Server Manager. Giữ cửa sổ server đang chạy, mở `http://localhost:8080`. Sau khi sửa frontend, nhấn Ctrl + Shift + R để nạp lại. Sau khi sửa backend, dừng server rồi chạy lại script.

Để chấm code cần cài/bật Docker Desktop và chuẩn bị image theo README của dự án. ZIP không đóng gói JDK, Ant, MySQL Server, Docker Desktop hoặc Docker image. Khi chạy trên LAN, dùng IP của máy nhận đang làm server; không giữ IP cũ của máy gửi. Mic/camera trên máy khác cần cấu hình HTTPS riêng theo `StudyFix/docs/HTTPS_LAN_MIC_CAMERA_VI.md`.

## Trao đổi bản sửa

Giữ một bản ZIP gốc. Khi gửi lại, có thể gửi các file source đã sửa cùng danh sách thay đổi; không gửi `.env`, database thật, chứng chỉ hoặc thư mục build. Gói có `SOURCE_MANIFEST_SHA256.txt` để kiểm tra bản source ban đầu. Những tài khoản trong `server/test/fixtures` chỉ phục vụ kiểm thử bằng dữ liệu tạm.
