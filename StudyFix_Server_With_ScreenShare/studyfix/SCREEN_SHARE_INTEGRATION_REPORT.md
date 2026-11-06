# Báo cáo tích hợp chia sẻ màn hình StudyFix

## Đã thêm vào source

- `src/studyfix/screenshare/ScreenShareWire.java`: giao thức packet nhị phân riêng cho luồng màn hình.
- `src/studyfix/screenshare/ScreenShareRelay.java`: listener TCP relay, ticket một lần, kiểm tra tư cách thành viên phòng qua `StudyFixGateway`, giới hạn một publisher/phòng, chuyển tiếp frame tới viewer cùng phòng và dọn phiên khi ngắt kết nối.
- `src/studyfix/Server.java`: khởi tạo relay trong vòng đời server, mặc định cổng relay `5001`, cấu hình qua `-Dstudyfix.screen.port`, và đóng relay khi server dừng.
- `src/studyfix/ClientHandler.java`: thêm `/screen-ticket publish` và `/screen-ticket view`; chỉ phiên đã đăng nhập và đang ở phòng mới được xin ticket. Token không được ghi vào log.
- `test/studyfix/ScreenShareRelayIntegrationTest.java`: test loopback TCP + SQLite, gồm kiểm tra ticket ngoài phòng bị từ chối, publisher/viewer handshake, chuyển tiếp JPEG thật, chặn publisher thứ hai, ticket một lần, STOP và dọn phiên khi thành viên rời phòng.
- `build.xml`: thêm bài test relay vào target `integration-test`.

## Kết quả kiểm tra trong môi trường dựng gói

Máy dựng gói có JDK 21, trong khi dự án yêu cầu Java 26. Do đó không thể tuyên bố đã chạy target Ant chính thức bằng JDK 26 tại đây. Thay vào đó đã biên dịch source và test với `javac --release 21` để kiểm tra tính tương thích Java, sau đó chạy trực tiếp các test trên loopback:

- `ScreenShareRelayIntegrationTest`: **19 kiểm tra PASS**, bao gồm tạo JPEG bằng `ImageIO`, relay qua TCP thật và giải mã ảnh ở phía viewer.
- `IntegrationTest`: **40 kiểm tra PASS**.
- `RecoveryTest`: **14 kiểm tra PASS**; phần kiểm tra giao diện Swing được bỏ qua trong môi trường headless.
- `HistoryTest`: **75 kiểm tra PASS**; phần kiểm tra giao diện Swing được bỏ qua trong môi trường headless.

Không chạy thành công toàn bộ target `ant integration-test` trong môi trường Linux này: một số test hiện có giả định đường dẫn `java.exe` theo Windows. Đây là giới hạn môi trường test, chưa phải bằng chứng lỗi của module relay. Cần chạy target chính thức trên máy Windows/JDK 26 của nhóm trước khi nhập vào server chính.

## Cách kiểm thử trên Windows

Trong PowerShell tại thư mục có `build.xml`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$ant = 'C:\Users\giang\Downloads\apache-ant-1.10.18-bin\apache-ant-1.10.18\bin\ant.bat'
& $ant clean jar
& $ant integration-test
```

Sau khi test đạt, chạy thử với database riêng để không ảnh hưởng dữ liệu hiện tại:

```powershell
java --enable-native-access=ALL-UNNAMED `
  '-Dfile.encoding=UTF-8' `
  '-Dstudyfix.port=5000' `
  '-Dstudyfix.screen.port=5001' `
  '-Dstudyfix.db=data/screen-share-test.db' `
  -cp 'dist\studyfix.jar;dist\lib\*' `
  studyfix.Server
```

Cổng TCP `5000` là điều khiển; `5001` là relay ảnh. Mở inbound cả hai cổng khi test giữa hai máy LAN.

## Giới hạn hiện tại

- Relay dùng TCP không mã hóa/TLS; chỉ thử nghiệm trong LAN tin cậy, không mở ra Internet công cộng.
- Cần test thêm giao diện Swing thực trên Windows và kết nối hai máy LAN.
- Tích hợp này chỉ bổ sung chia sẻ màn hình; chưa bổ sung nghiệp vụ bài tập, worker chấm, thi hoặc bảng xếp hạng.
