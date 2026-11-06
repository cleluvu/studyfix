# StudyFix — tích hợp chia sẻ màn hình phía server

Module chia sẻ màn hình đã được ghép trực tiếp vào source của dự án này. Không cần chép patch thủ công.

## Thay đổi chính

- `src/studyfix/screenshare/ScreenShareWire.java`: framing nhị phân cho kênh ảnh, dùng 4 byte big-endian độ dài + 1 byte type + payload; tương thích với `ScreenShareWire` trong client Member 2.
- `src/studyfix/screenshare/ScreenShareRelay.java`: cổng TCP relay, ticket một lần hết hạn sau 30 giây, một publisher mỗi phòng, viewer nhận frame mới nhất, xác thực membership và dọn kết nối khi phiên rời phòng.
- `src/studyfix/Server.java`: khởi tạo relay khi khởi tạo server; mặc định điều khiển cổng 5000, relay cổng 5001; đóng relay trong `close()`.
- `src/studyfix/ClientHandler.java`: lệnh `/screen-ticket publish` và `/screen-ticket view`, chỉ cấp ticket nếu phiên đã đăng nhập và đang ở phòng; response `[SCREEN_TICKET] mode=... room=... port=... ticket=...`. Token không được ghi vào log.
- `test/studyfix/ScreenShareRelayIntegrationTest.java`: kiểm thử đầu-cuối TCP với SQLite.
- `build.xml`: thêm test relay vào target `integration-test`.

## Chạy trên Windows (JDK 26)

Dừng instance server đang chạy trước khi test; mặc định cổng 5000 và 5001 không thể dùng đồng thời bởi hai instance. Trong PowerShell ở thư mục chứa `build.xml`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\giang\Downloads\apache-ant-1.10.18-bin\apache-ant-1.10.18\bin\ant.bat' clean jar
& 'C:\Users\giang\Downloads\apache-ant-1.10.18-bin\apache-ant-1.10.18\bin\ant.bat' integration-test
```

Đường dẫn JDK/Ant có thể cần chỉnh theo máy. Test relay chọn cổng ngẫu nhiên riêng để giảm xung đột, còn các test server hiện có vẫn sử dụng mặc định 5001; nên dừng server thật trước khi chạy toàn bộ suite.

Chạy server console trên cổng mặc định:

```powershell
java --enable-native-access=ALL-UNNAMED `
  '-Dfile.encoding=UTF-8' `
  '-Dstudyfix.port=5000' `
  '-Dstudyfix.screen.port=5001' `
  '-Dstudyfix.db=data/screen-share-test.db' `
  -cp 'dist\studyfix.jar;dist\lib\*' `
  studyfix.Server
```

Dùng database thử riêng để không thay đổi dữ liệu học tập hiện có. Trong một terminal riêng, chạy client Member 2. Đăng nhập giáo viên, tạo phòng; học sinh tham gia; nhấn **Bắt đầu chia sẻ** ở client publish và **Xem màn hình đang chia sẻ** ở viewer.

## Giới hạn

- Relay đã có kiểm soát membership và token một lần, nhưng giao thức hiện không mã hóa TLS. Chỉ dùng trên LAN tin cậy; không mở cổng 5001 ra Internet công cộng nếu chưa bổ sung bảo mật truyền tải.
- Bộ test tự động kiểm tra packet relay và lifecycle qua loopback; cần kiểm tra thêm JPEG thật, Windows Firewall và hai máy LAN trên máy nhóm.
- Các nghiệp vụ giao bài, worker chấm, thi và xếp hạng vẫn chưa được bổ sung trong source này.
