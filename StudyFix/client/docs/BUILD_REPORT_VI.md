# Build report — bản client Member 2

Ngày cập nhật giao diện: 2026-10-25.

## Kết quả thực tế

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| Biên dịch toàn bộ source client | PASS | `javac --release 17 -encoding UTF-8`; đã biên dịch lại sau thay đổi UI vùng bản nháp |
| Biên dịch test source | PASS | `ClientSelfTest.java` |
| Smoke tests | PASS | Frame UTF-8, ráp history parts, parser server text, command multiline, loopback TCP fake server, screen packet bytes; `ClientSelfTest` chạy lại PASS sau thay đổi UI |
| Tạo executable JAR | PASS | `target/studyfix-client-1.0.0.jar` |
| Compile screen relay add-on | PASS (API-shape only) | Compile với stub thể hiện public API của `StudyFixGateway`; chưa link/run cùng server JDK 26 |
| `mvn clean test package` | NOT RUN | Maven không có trong môi trường tạo gói |
| Kết nối client với server thật | NOT RUN | Môi trường chỉ có JDK 21; server baseline được cấu hình `--release 26` |
| GUI visual test trên Windows | NOT RUN | Phải test trên máy nhóm với desktop Windows và server LAN thật |
| Live screen-share e2e | PENDING | Cần merge module relay và `/screen-ticket` vào server |
| Exercise/submit/judge/contest e2e | PENDING | Server baseline chưa có các command/nghiệp vụ này |

## Diễn giải

`PASS` cho compile và smoke tests không có nghĩa mọi use case đã chạy trên server thật. Trước khi kết luận hoàn thành, phải chạy checklist trong `TEST_PLAN.md`: login nhiều client, room isolation, chat, member list, xin quyền sửa, draft/finish/cancel, stale version/token, history và module màn hình sau khi tích hợp.
