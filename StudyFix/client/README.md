# StudyFix Client — thành viên 2 + module bài tập thành viên 3

Client Java Swing này kết nối server StudyFix hiện có và có tab Bài tập/Nộp bài dùng chung ứng dụng. Client dùng Java 17 trở lên, không phụ thuộc thư viện Java bên ngoài.

## Chức năng

- Đăng nhập; tạo/vào/rời phòng; thành viên; chat trong phòng.
- Code cộng tác: xin/nhả quyền, chọn vùng dòng, sửa bản nháp trực tiếp, xem trước, hoàn tất/hủy và lịch sử.
- Chia sẻ màn hình: chụp desktop, nén JPEG, kết nối relay server qua cổng 5001. Để truyền qua mạng, server phải chạy với relay tích hợp.
- **Member 3:** ngân hàng đề, giao bài chung/cá nhân, editor cá nhân, nộp source snapshot riêng hoặc snapshot code chung, lịch sử/kết quả, thi có thời hạn và bảng xếp hạng. Module bài tập kết nối cổng 5002; server phải chạy bằng `run-with-assignments.ps1`.

## Build và test

Mở PowerShell tại thư mục `studyfix-client` (nếu Windows chặn script, chỉ áp dụng bypass trong phiên PowerShell hiện tại):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-ExecutionPolicy -Scope Process Bypass
.\build.ps1
.\member3-self-test.ps1
```

`build.ps1` sẽ xóa `target` rồi build lại, vì vậy phải đóng mọi cửa sổ client đang chạy trước. Để mở client sau khi build:

```powershell
.\run-client-only.ps1
```

Có thể chạy lệnh `run-client-only.ps1` từ các cửa sổ PowerShell khác nhau để mở nhiều client độc lập, không build lại.

## Tài khoản demo

Server tự seed các tài khoản demo khi database `data/studyfix.db` được tạo lần đầu:

| Username | Password | Role |
|---|---|---|
| `teacher` | `Teacher123!` | TEACHER |
| `teacher2` | `Teacher234!` | TEACHER |
| `student1` | `Student123!` | STUDENT |
| `student2` | `Student234!` | STUDENT |

Nếu database đã tồn tại, dùng tài khoản đã được tạo trong database đó.

## Kênh mạng

- TCP 5000: điều khiển, xác thực, phòng/chat/code cộng tác.
- TCP 5001: relay chia sẻ màn hình.
- TCP 5002: API bài tập/nộp bài/thi của Member 3.

Module 3 dùng một kết nối riêng ở cổng 5002; không chiếm reader của kết nối điều khiển 5000 hoặc relay 5001. Mọi role/membership/điểm chính thức đều được server xác minh.

## An toàn chấm bài

Server chỉ chạy source học sinh trong Docker sandbox và cấu hình fail-closed. Nếu Docker/image chưa sẵn sàng, kết quả là `SANDBOX_UNAVAILABLE`, không fallback chạy code học sinh trực tiếp trong JVM server. Đọc hướng dẫn tại `docs/MEMBER3_CLIENT_GUIDE_VI.md` và tài liệu server `README_MEMBER3_VI.md`.

## Kiến trúc module

- `vn.studyfix.client.exercise.ExerciseModule`: tab bài tập, giao bài, làm bài cá nhân, kết quả, thi và xếp hạng.
- `AssignmentConnection`: request/response TCP riêng, frame 4 byte big-endian + UTF-8 JSON (tối đa 64 KiB).
- `MainFrame.java`: một điểm tích hợp UI để hiển thị tab module mới; các service/login/chat/code/screen share hiện có được giữ nguyên.
- `docs/MEMBER3_CLIENT_GUIDE_VI.md`: hướng dẫn test module 3.
- `docs/SERVER_PROTOCOL_MAP.md`, `docs/TASK2_COMPLETE_PLAN.md`, `docs/TEST_PLAN.md`: tài liệu phần client/core còn lại.
