# Báo cáo tích hợp StudyFix — Screen Share + Member 3

## Các module mới

- `src/studyfix/screenshare/**`: relay TCP màn hình trên cổng 5001 (đã có trong baseline server được nhận).
- `src/studyfix/assignment/**`: ngân hàng bài, model, SQLite repository riêng, assignment service, TCP listener cổng 5002, Docker grader, contest và leaderboard.
- `test/studyfix/assignment/**`: self-test repository/JSON và integration test login → phòng → assignment → personal/shared submission → private results.
- `run-with-assignments.ps1`: build và chạy `AssignmentEnabledServerMain`; `test-assignment-module.ps1`: chạy test module 3; `test-all.ps1`: chạy core regression rồi test module 3.

## Core baseline

Các file `Server.java`, `ClientHandler.java`, `Room.java`, `Protocol.java`, `Store.java`, `StudyFixGateway.java` và `build.xml` được giữ nguyên trong tích hợp Member 3. Launcher mới khởi tạo core `Server`, lấy `gateway()` rồi bật assignment listener mà không sửa `Server.main()` hay command router.

## Ports/database

- 5000 control; 5001 screen relay; 5002 assignment API.
- `data/studyfix.db` vẫn là database lõi.
- `data/studyfix-learning.db` là DB module 3, có schema version và migration additive của riêng module.

## Bài kiểm tra ghi nhận trong môi trường phát triển

Các kiểm tra mới được chạy với OpenJDK 21 bằng `javac --release 21` do môi trường build hiện tại không có JDK 26; server project chính thức yêu cầu JDK 26. Kết quả gần nhất: `AssignmentModuleSelfTest` PASS 13 checks; `AssignmentProtocolIntegrationTest` PASS 20 checks; client `ClientSelfTest` PASS và `ExerciseModuleSelfTest` là test codec thêm mới. Test tích hợp hiện xác nhận API, quyền truy cập, tính riêng tư và trạng thái fail-closed. **Grading thật bằng Docker không được tuyên bố PASS nếu Docker engine/image không sẵn sàng.**

## Hạn chế còn lại

- Cần chạy `ant clean jar`, `ant integration-test` và `test-assignment-module.ps1` trên máy JDK 26 của nhóm để nghiệm thu đúng toolchain.
- Grading hoàn chỉnh trong demo phụ thuộc Docker Desktop và image `eclipse-temurin:21-jdk` đã được chuẩn bị. Không tự fallback về thực thi code trong JVM server.
- Cần test đầu cuối giao diện Swing trên Windows và 3 client/2 phòng trước demo cuối cùng.
