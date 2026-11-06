# F11 — đã sửa lỗi hiển thị lịch sử xen thông báo local

## Nguyên nhân

Client trước đây in mỗi frame HISTORY_PART bằng một lần `System.out.print`. Luồng bàn phím in heartbeat/hướng dẫn/lỗi file bằng `println` riêng; PrintStream chỉ khóa từng lần in nên thông báo có thể nằm giữa các phần của một lịch sử. TCP và SQLite không mất code; lỗi nằm ở thứ tự hiển thị console.

## Bản sửa

- `src/studyfix/Client.java`: `ConsoleOutput` điều phối tất cả thông báo sau khi kết nối. Trong khi nhận/in multipart, giữ các thông báo local chờ đến sau phần cuối rồi in theo thứ tự. Không giữ khóa khi đọc socket, đọc bàn phím hoặc gửi lệnh; không phải gom toàn bộ history vào RAM. Kiểm tra số thứ tự/tổng số phần. Nếu kết nối kết thúc giữa chừng, báo lịch sử chưa nhận đủ và xả thông báo đang chờ trước khi thoát.
- `test/studyfix/ConsoleHistoryInterleaveTest.java`: sau khi thấy phần đầu, gửi heartbeat off/on và lệnh load sai; nhận lệnh ACK qua socket để xác nhận bàn phím vẫn tiến triển. Sau phần cuối, xác nhận nguyên chuỗi Unicode 210025 byte/4 frame còn liền mạch và các thông báo chỉ xuất hiện sau đó. Có thêm ca EOF khi chưa nhận đủ phần, đảm bảo thông báo không mất và client không treo.
- `build.xml`: đưa regression này vào `ant integration-test`; giữ cấu hình NetBeans/JDK 26.
- `README.md`: mô tả hành vi hoãn thông báo local và test hồi quy.

## Bằng chứng

- [baseline-old-client.log](baseline-old-client.log): chạy **test mới** với bytecode client cũ được giữ từ lượt audit, exit 1 đúng mong đợi; regression vẫn bắt được lỗi cũ.
- [interleave.log](interleave.log): chạy test mới với client đã sửa, hai ca đều PASS.
- [integration.log](integration.log): **BUILD SUCCESSFUL**, 59 giây; RecoveryTest 22, HistoryTest 78, IntegrationTest 40, hai ca interleave/EOF và sáu assertion ConsoleClientTest đều PASS. Không bỏ qua Swing vì headless trong lượt này.
- JAR mới được build tại `dist/studyfix.jar`. Database thử riêng, client đến localhost; không mở/sửa database thật. Các JVM kiểm thử và process client con đã kết thúc.

Chạy lại từ thư mục project:

```powershell
& 'C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat' integration-test
```

Báo cáo `verification/person-one-20261021/REPORT.md` là bằng chứng **trước khi sửa**, không được ghi đè thành kết quả mới. Lượt này xác minh F11 và chạy lại suite tích hợp; không tuyên bố đã chạy lại toàn bộ ma trận 109 mã hoặc đã test LAN thật.
