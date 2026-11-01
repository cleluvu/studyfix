# StudyFix — chạy module Bài tập / Chấm bài / Thi (Thành viên 3)

## Cổng và phạm vi

- TCP `5000`: server lõi StudyFix (đăng nhập, phòng, chat, code cộng tác).
- TCP `5001`: relay chia sẻ màn hình hiện có.
- TCP `5002`: listener mới của module bài tập.
- `data/studyfix.db`: database lõi; giữ nguyên để không mất tài khoản/phòng.
- `data/studyfix-learning.db`: database riêng của module 3; không dùng `Store.java`, không sửa schema core.

**Không dùng `ant run` nếu muốn bật bài tập**: target này khởi chạy `studyfix.Server` cũ và không bật listener cổng 5002. Dùng `run-server-manager.ps1` để chạy đủ ba listener cùng cửa sổ Swing Server Manager, hoặc `run-with-assignments.ps1` nếu chỉ muốn chạy console. Không chạy cả hai cùng lúc.

## Chạy trên Windows

Mở PowerShell tại project server; đóng server cũ đang dùng 5000/5001 trước. Đặt `JAVA_HOME` trỏ đến JDK 26. Script tự tìm Ant từ `PATH`, `ANT_HOME` hoặc thư mục Downloads của tài khoản hiện tại.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-ExecutionPolicy -Scope Process Bypass
.\run-with-assignments.ps1
```

Để kiểm thử module 3 (dùng database tạm cho test, không đụng `data/studyfix.db`):

```powershell
.\test-assignment-module.ps1
```

Nên chạy thêm test hồi quy lõi: `& $ant integration-test`. Test mới module 3 được chạy riêng bởi script trên.

## Docker grading

Worker chấm code chạy bằng Docker, mặc định image `eclipse-temurin:21-jdk`, `--network none`, filesystem root read-only, giới hạn CPU/RAM/PID, timeout và giới hạn output. Cần Docker Desktop/engine hoạt động và image đã có sẵn; vì runtime dùng `--pull=never`, chuẩn bị image trước khi demo (có Internet thì `docker pull eclipse-temurin:21-jdk`, nếu triển khai LAN thì nhập image từ máy build).

Nếu Docker hoặc image chưa sẵn sàng, submission vẫn được lưu và sẽ trả verdict `SANDBOX_UNAVAILABLE`; module **không** tự rơi về chạy code học sinh trong JVM server. Đây là hành vi fail-closed có chủ đích.

## Các chức năng module 3

- Ngân hàng bài có seed idempotent và public/hidden tests.
- Giao bài cả phòng hoặc cá nhân tới học sinh đang trong phòng.
- Nộp `PERSONAL` (snapshot code riêng của học sinh) hoặc `SHARED_SNAPSHOT` (snapshot code chung tại thời điểm nộp).
- Lưu receipt, lịch sử, trạng thái queue/running/final, verdict, điểm và kết quả test an toàn.
- Thi có thời điểm bắt đầu/kết thúc theo server, gửi `contestId` khi nộp và bảng xếp hạng.

## Bảo vệ baseline

Module 3 được thêm trong `src/studyfix/assignment/**` và `test/studyfix/assignment/**`. Không chỉnh `Server.java`, `ClientHandler.java`, `Room.java`, `Protocol.java`, `Store.java`, `StudyFixGateway.java` hay `build.xml`. Core port 5000 và screen relay 5001 giữ nguyên.
