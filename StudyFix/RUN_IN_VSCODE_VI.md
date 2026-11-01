# Hướng dẫn chạy StudyFix trong VS Code (Windows)

## 1. Yêu cầu
- JDK 26 tại `C:\Program Files\Java\jdk-26.0.2.1`.
- Apache Ant đã cài. Script tự tìm `ant.bat` trong PATH, `ANT_HOME`, hoặc Downloads.
- Docker Desktop đã mở nếu muốn chấm code.

## 2. Mở giao diện Server Manager
Mở thư mục gốc project `StudyFix` bằng VS Code. Mở Terminal (PowerShell) và chạy:

```powershell
cd .\server
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ANT_HOME = "$env:USERPROFILE\Downloads\apache-ant-1.10.18-bin\apache-ant-1.10.18"
$env:Path = "$env:ANT_HOME\bin;$env:Path"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\run-server-manager.ps1
```

Đừng đóng terminal/cửa sổ Server Manager trong lúc test. Giao diện quản trị hiển thị kết nối, phòng, code/history, log, hàng đợi/worker chấm và trạng thái Web Gateway. Cùng một JVM khởi động bốn dịch vụ:
- Control: TCP 5000
- Screen share relay cho client Swing: TCP 5001
- Assignment/grading: TCP 5002
- Web FE + API Gateway: HTTP `http://localhost:8080` (có thể đổi bằng `STUDYFIX_WEB_PORT`)

Mở `http://localhost:8080` để dùng FE web mới. Các client web dùng chung tài khoản với server hiện có; không tạo user/database riêng. Để truy cập từ máy khác trong LAN, mở `http://IP_CUA_MAY_CHU:8080` sau khi cho phép cổng 8080 qua Windows Firewall. HTTP không mã hóa mật khẩu; chỉ dùng trên localhost/mạng tin cậy. Để dùng screen share của browser hoặc triển khai trên mạng không tin cậy, cấu hình HTTPS với chứng chỉ được tin cậy hoặc reverse proxy TLS.

Gateway hỗ trợ keystore PKCS12 sẵn có qua biến môi trường PowerShell (không ghi mật khẩu thẳng vào script):
```powershell
$env:STUDYFIX_WEB_PORT = '8443'
$env:STUDYFIX_WEB_KEYSTORE = 'C:\certs\studyfix.p12'
$env:STUDYFIX_WEB_KEYSTORE_PASSWORD = 'mat-khau-keystore'
.\run-server-manager.ps1
```
Chứng chỉ cần có SAN trùng với hostname/IP người dùng nhập trên trình duyệt; chứng chỉ tự ký chưa được tin cậy có thể vẫn bị trình duyệt chặn screen capture.

**Không chạy `run-with-assignments.ps1` cùng lúc** với Server Manager vì cả hai đều dùng các cổng trên.

Nếu script báo `ant.bat` không tìm thấy, xác nhận `ANT_HOME` đang trỏ tới thư mục chứa `bin\ant.bat`. Nếu thư mục Ant khác, thay đường dẫn tương ứng.

## 3. Build Client một lần
Mở Terminal mới trong VS Code:

```powershell
cd .\client
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\build.ps1
```

Đóng mọi cửa sổ Client cũ trước khi build vì `build.ps1` xoá thư mục `target` để tạo JAR mới. Không cần tắt Server Manager.

## 4. Mở nhiều cửa sổ Client (teacher/student)
Trong Terminal Client sau khi build thành công, chạy:

```powershell
.\run-client-only.ps1
```

Mỗi Client phải được mở bằng một tiến trình riêng. Để mở Client tiếp theo:
1. Bấm `+` trong Terminal của VS Code để tạo terminal mới.
2. Nếu Terminal mới ở thư mục gốc project, chạy `cd .\client`. Nếu đã ở `client`, không cần `cd` thêm.
3. Đặt `JAVA_HOME`/`Path` trong terminal mới nếu cần.
4. Chạy `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force`.
5. Chạy đúng lệnh PowerShell: `.\run-client-only.ps1`

Không chạy `build.ps1` hoặc `run.ps1` khi các Client khác còn mở. Hai script đó rebuild/xoá JAR và có thể lỗi file đang được sử dụng.

## 5. Tài khoản demo
Khi database `data/studyfix.db` được tạo lần đầu, server seed các tài khoản:
- Giáo viên: `teacher` / `Teacher123!`
- Giáo viên 2: `teacher2` / `Teacher234!`
- Học sinh 1: `student1` / `Student123!`
- Học sinh 2: `student2` / `Student234!`

Nếu database đã có sẵn, đăng nhập bằng tài khoản đã tồn tại. Đừng xoá `data/studyfix.db` hoặc `data/studyfix-learning.db` để xử lý lỗi nếu muốn giữ tài khoản/lịch sử hiện có.

## 6. Quy trình test cơ bản
1. Server Manager: xác nhận 5000/5001/5002 đang chạy.
2. Client 1 đăng nhập `teacher`; tạo phòng.
3. Client 2 đăng nhập `student1`; Client 3 đăng nhập `student2`; cả hai vào đúng mã phòng.
4. Test chat, code cộng tác, yêu cầu/chuyển quyền sửa, lịch sử, screen share.
5. Vào tab Bài tập để giao bài; thử sửa lỗi cùng phòng, kiểm tra code chung; thử bài viết code từ đầu ở từng học sinh.
6. Nếu chấm báo `SANDBOX_UNAVAILABLE`, mở Docker Desktop và chờ Engine sẵn sàng; kiểm tra image `eclipse-temurin:21-jdk` có sẵn nếu máy không có mạng.

## 7. Dừng
- Dùng nút `Dừng toàn bộ dịch vụ` trong Server Manager để dừng dịch vụ. Đóng cửa sổ sau khi trạng thái đã dừng.
- Mở Client mới trong một terminal khác không tạo server khác.
- Nếu đổi mã nguồn client, đóng tất cả Client trước khi chạy lại `build.ps1`.

## 8. Bản cập nhật UI/UX, test và dữ liệu
- Web FE mới được phục vụ ngay từ Java Web Gateway ở cổng 8080; FE tĩnh không cần `npm install` để chạy. Source hiện tại là HTML/CSS/JavaScript không dependency, chưa phải bản React/TypeScript/Vite được build.
- Swing client vẫn được giữ làm fallback; Server Manager quản lý core TCP, relay, assignment/grading và gateway trong cùng runtime.
- Đọc `docs/StudyFix_UI_UX_Redesign_Plan.md`, `docs/IMPLEMENTATION_STATUS_VI.md`, `docs/API_CONTRACT.md` và `docs/MIGRATION_AND_ROLLBACK.md` trước khi đưa vào môi trường đang có dữ liệu.
- Trên Windows, chạy `server\test-all.ps1` (JDK 26 + Ant + GUI desktop khả dụng) để regression, và `client\self-test.ps1` để kiểm tra client. Môi trường Linux của bản chuẩn bị không thay thế nghiệm thu UI/Windows/Docker.
