# Danh tính và trạng thái kết nối sinh viên qua LAN

> Tài liệu này mô tả chế độ SQLite ban đầu. Dự án hiện có thêm chế độ MySQL cho quản trị người dùng/lớp và WebSocket. Xem [hướng dẫn MySQL và WebSocket](MYSQL_ADMIN_WEBSOCKET_VI.md) để cấu hình bản hiện tại; bảng kết nối lớp vẫn dùng được trong chế độ tương thích.

## 1. Kiến trúc đã kiểm tra

- Giao diện sử dụng `web-client/static`: HTML/CSS và JavaScript ES modules, không dùng framework SPA bên ngoài.
- `WebGatewayServer` nhận HTTP/HTTPS ở cổng 8080 mặc định. Đăng nhập qua kết nối TCP nội bộ tới server lõi cổng 5000; lõi xác thực tại `Store.login()`.
- Dữ liệu tài khoản và lớp nằm trong SQLite lõi (`server/data/studyfix.db` khi chạy bằng launcher). Ngân hàng bài và bài nộp nằm trong database learning riêng; thay đổi này không sửa luồng chấm Docker.
- Dự án chưa có WebSocket. `/api/events` được gọi bằng polling khoảng 1,6 giây, có cờ chống gọi chồng. Tiếp tục dùng polling và API lớp hiện tại, không bổ sung một kiến trúc truyền sự kiện khác.
- Mật khẩu tiếp tục sử dụng PBKDF2-HMAC-SHA256, 600.000 vòng, salt ngẫu nhiên 16 byte, khóa 256 bit. Không chuyển sang lưu mật khẩu rõ.

## 2. Danh tính và mã sinh viên

`userId` là khóa người dùng, `studentCode` là mã sinh viên trong `users.student_code`. Đăng nhập nhận `{studentCode, password}`; `{username, password}` vẫn tương thích với client cũ và tài khoản giảng viên. Lõi tra database và kiểm tra hash; gateway lấy danh tính chuẩn từ lõi sau xác thực, không coi chuỗi do trình duyệt gửi là danh tính đã xác thực.

Migration thêm cột `student_code` và chỉ mục duy nhất. Với tài khoản sinh viên cũ, mã ban đầu được giữ bằng username hiện tại để không làm hỏng đăng nhập cũ. **Không tự sinh MSSV thật từ tên hay IP.** Nếu tài khoản của bạn hiện là `student1`, mã tương thích ban đầu là `student1`. Để dùng MSSV thật, dữ liệu tài khoản phải được cấp đúng mã đó trong `users.student_code` (không sửa `user_id` hay tự ghi mật khẩu rõ). Chức năng này quản lý danh sách kết nối của tài khoản đã được cấp, không thêm một hệ thống đăng ký tài khoản mới.

Mỗi lần đăng nhập có token web, Session ID TCP lõi và mã kết nối hiển thị độc lập. Hai lần đăng nhập cùng một tài khoản hoặc cùng IP vẫn là hai phiên khác nhau. Session ID lõi và token của sinh viên không được trả cho giảng viên. `connectionId` là mã theo dõi không có quyền xác thực; giao diện chỉ hiển thị phần rút gọn của mã này.

## 3. IP, heartbeat và vòng đời

1. Gateway lấy IP bằng `HttpExchange.getRemoteAddress().getAddress().getHostAddress()`. Không lấy IP từ form hoặc JavaScript.
2. Không tin `X-Forwarded-For`, `Forwarded` hay header IP do client gửi. Hiện chưa cấu hình danh sách proxy tin cậy, nên luôn ghi IP của peer kết nối trực tiếp tới gateway. Nếu có reverse proxy, địa chỉ ghi nhận sẽ là IP proxy; không tuyên bố đó là IP thiết bị cuối.
3. Sau xác thực, server ghi `userId`, Session ID nội bộ, IP, thời gian đăng nhập, hoạt động cuối và trạng thái Online vào database lõi. Password/token web không được đưa vào bảng nhật ký này.
4. Các request đã xác thực, bao gồm polling `/api/events`, đóng vai trò heartbeat từ trình duyệt. API `POST /api/auth/heartbeat` cũng có thể được dùng để kiểm thử riêng. Ping TCP 8 giây giữa gateway và lõi **không** kéo dài sự sống của trình duyệt đã mất mạng.
5. Timeout mặc định là **120 giây** không có request xác thực. Bộ dọn dẹp chạy mỗi giây: chuyển Offline, thu hồi token, đóng TCP nội bộ và dọn media. Thời gian xử lý thực tế còn phụ thuộc tải và database. Có thể chỉnh bằng JVM property `-Dstudyfix.web.sessionTimeoutMillis=120000`; kiểm thử tự động dùng 2 giây để không phải chờ 2 phút.
6. Đăng xuất thu hồi đúng phiên và ghi Offline ngay; không đóng phiên khác của cùng tài khoản. Lõi TCP đóng thì gateway ghi Offline trong lần dọn tiếp theo hoặc khi được truy cập lại.
7. Refresh bình thường giữ token trong sessionStorage; request mới hủy lịch rời trang 8 giây hiện có. Mất mạng lâu hơn timeout thì token cũ không được phục hồi; người dùng đăng nhập lại để tạo phiên mới. Lịch sử phiên cũ vẫn Offline.
8. Khi khởi động lại, các dòng còn Online do lần dừng bất thường được chuyển Offline, lý do `SERVER_RESTART`; không phục hồi nhầm trạng thái Online.

“Hoạt động cuối” là thời điểm server nhận request xác thực gần nhất, không phải thời điểm sinh viên gõ phím cuối cùng. Dữ liệu thời gian được ghi xuống SQLite theo nhịp tối đa khoảng 2 giây/lần/phiên để tránh ghi mỗi request; thay đổi phòng được ghi ngay khi quan sát thấy.

## 4. Danh sách lớp và phân quyền

Trong cuộc họp, giảng viên bấm **Thành viên** → **Kết nối sinh viên**. Mỗi dòng cho biết MSSV, họ tên, IP, Online/Offline, thời gian đăng nhập, hoạt động cuối và mã theo dõi rút gọn. Một sinh viên có nhiều thiết bị sẽ có nhiều dòng. Số sinh viên Online được đếm theo `userId`, số kết nối Online được đếm theo từng phiên.

`GET /api/rooms/current` chỉ thêm `room.studentConnections` cho vai trò `TEACHER`. `GET /api/rooms/current/presence` yêu cầu đăng nhập, vai trò giảng viên và đang là thành viên của đúng lớp hiện tại. Sinh viên bị từ chối 403; chưa đăng nhập là 401; chưa vào lớp là 409. Không có API chọn tùy ý roomId để xem IP lớp khác.

Hệ thống hiện có hai role `TEACHER` và `STUDENT`, chưa có tài khoản web `ADMIN`. Không tự thêm role hoặc nới quyền. Server Manager vẫn là công cụ quản trị phía máy chủ; dữ liệu riêng của bảng này chỉ được cung cấp qua web cho giảng viên đã vào lớp. Bộ render cũng kiểm tra vai trò để tránh hiển thị nhầm dữ liệu đã cache.

Rời cuộc họp chỉ đóng giao diện/media và giữ tư cách thành viên lớp. Bấm rời phòng học mới kết thúc hiện diện trong lớp. Một phiên vẫn đăng nhập nhưng đã rời lớp được ghi Offline ở lớp cũ (`LEFT_ROOM`), không bị đăng xuất khỏi hệ thống. Danh sách giữ các phiên đã từng vào lớp để giảng viên thấy được người vừa mất kết nối; bảng thành viên đang hoạt động vốn có không bị thay bằng lịch sử Offline.

Phạm vi theo dõi IP ở đây là **web client đang sử dụng**. TCP 5000 nội bộ của web gateway không được báo nhầm là địa chỉ máy sinh viên. Console TCP cũ không có dữ liệu phiên HTTP trong bảng quản lý web này.

## 5. File và migration

| File | Thay đổi |
|---|---|
| `server/src/studyfix/Store.java` | Chạy migration, tra mã sinh viên, trả username chuẩn; giữ hashing hiện có. |
| `server/src/studyfix/PresenceRepository.java` | Migration, lưu phiên, hiện diện theo lớp, Offline và khôi phục sau restart bằng giao dịch SQLite hiện có. |
| `server/src/studyfix/WebGatewayServer.java` | Lấy IP thật, liên kết phiên, heartbeat/timeout, kiểm tra quyền và API danh sách. |
| `web-client/static/features/auth/auth.js` | Gửi trường mã sinh viên khi đăng nhập. |
| `web-client/static/ui/render.js` | Nhãn đăng nhập phù hợp MSSV và tài khoản giảng viên. |
| `web-client/static/features/room/student-connections.js` | Render danh sách riêng của giảng viên, escape dữ liệu, đếm sinh viên và thiết bị. |
| `web-client/static/features/room/room.js` | Đưa danh sách vào bảng Thành viên của lớp. |
| `web-client/static/core/polling.js` | Cập nhật bảng đang mở khi dữ liệu kết nối thay đổi; không thay code đang sửa. |
| `web-client/static/meeting.css` | Bố cục thẻ kết nối, màu Online/Offline. |
| `server/test/studyfix/LanPresenceIntegrationTest.java` | Kiểm thử API, quyền, mã sinh viên khác username, IP, nhiều phiên, heartbeat, EOF, restart. |
| `server/test/studyfix/ClassroomIntegrationTest.java` | Fixture thêm tài khoản dùng tên cột tường minh sau migration. |
| `server/test-lan-presence.ps1`, `server/build.xml` | Lệnh kiểm thử riêng và tích hợp vào hồi quy Ant. |
| `web-client/tests/check_student_connections.mjs` | Kiểm tra render, quyền, đếm nhiều thiết bị và escape dữ liệu. |

Migration tự động, không cần xóa database: thêm `users.student_code`, `network_sessions`, `classroom_connections` và các index. `network_sessions.session_id` chỉ dùng phía server; API đọc có danh sách trường tường minh, không `SELECT *` rồi gửi toàn bộ cho trình duyệt. Những phiên Offline được giữ trong database; hiện chưa có chính sách tự xóa lịch sử lâu ngày.

## 6. Chạy và kiểm thử bằng hai máy LAN

1. Trên máy server, đóng Server Manager cũ để launcher biên dịch được JAR. Không xóa database hay chạy `ant clean` để khắc phục JAR đang bị khóa. Từ thư mục gốc dự án chạy:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
   ```

2. Server tự migration khi mở database. Chạy `ipconfig` trên máy server và lấy IPv4 của card Wi-Fi/Ethernet cùng mạng với máy sinh viên. Ví dụ nếu IP vẫn là `192.168.20.251`, máy khác truy cập `http://192.168.20.251:8080`. Không nhập `localhost` trên máy sinh viên để tìm server ở máy khác. Nếu launcher đã cấu hình HTTPS thì dùng HTTPS đúng cấu hình.
3. Cho phép ứng dụng Java/cổng TCP 8080 qua Windows Firewall cho mạng LAN phù hợp. Kiểm tra từ máy sinh viên:

   ```powershell
   Test-NetConnection 192.168.20.251 -Port 8080
   ```

   `TcpTestSucceeded: True` xác nhận mở được TCP; chưa phải xác nhận đăng nhập hay media. LAN không được bật cô lập client trên Wi-Fi khách.
4. Máy server đăng nhập giảng viên, tạo hoặc vào lớp. Máy thứ hai đăng nhập bằng mã tài khoản sinh viên và mật khẩu đã cấp trong DB, vào cùng lớp. Bấm Ctrl+Shift+R nếu trình duyệt còn giữ module cũ.
5. Ở máy giảng viên, mở **Thành viên**. Đối chiếu IP với `ipconfig` trên máy sinh viên. Truy cập trực tiếp LAN phải thấy địa chỉ máy sinh viên, không phải `127.0.0.1` của TCP nội bộ. Truy cập `localhost` trên chính máy server thì thấy loopback là đúng.
6. Mở thêm trình duyệt khác hoặc cửa sổ ẩn danh trên máy sinh viên, đăng nhập cùng mã và vào lớp. Phải thấy hai kết nối Online, một sinh viên Online. Đăng xuất một cửa sổ: một dòng Offline, cửa sổ còn lại vẫn Online.
7. Tắt Wi-Fi/rút cáp ở máy sinh viên, giữ trang đang mở. Sau khoảng 120 giây cộng thời gian dọn/cập nhật, dòng chuyển Offline. Bật mạng lại sau khi hết hạn: phiên cũ bị từ chối, đăng nhập lại tạo một dòng Online mới; dòng cũ vẫn Offline. Thử mất mạng ngắn hơn timeout để kiểm tra tiếp tục phiên.
8. Sinh viên mở DevTools, gọi `fetch('/api/rooms/current/presence', {headers:{Authorization:'Bearer '+sessionStorage.getItem('studyfix.web.token')}})` trên trình duyệt của mình: phải nhận 403. Không sao chép token sang máy/người khác.
9. Kiểm tra lại chat, Master → nhánh → chạy kiểm tra → Merge, luyện/nộp code và media. Việc xem bảng kết nối không được sửa code hoặc làm mất bản nháp. Media LAN vẫn cần HTTPS theo cơ chế trình duyệt hiện có.

## 7. Kiểm thử tự động và giới hạn chứng cứ

Từ thư mục gốc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\test-lan-presence.ps1
node .\StudyFix\web-client\tests\check_student_connections.mjs
```

Script dùng JDK hiện có, biên dịch vào `server/build/lan-presence-test`, mở cổng ngẫu nhiên và tạo database tạm dưới `server/build/lan-presence-*`. Không sửa dữ liệu vận hành, không cần Docker để kiểm tra hiện diện. Ngoài ra có target Ant `lan-presence-test` và được thêm vào `integration-test`.

Kiểm thử mạng tự động chạy trên localhost, xác nhận nguồn IP là peer HTTP và header giả không thay thế IP đó. Đây không phải bằng chứng đã chạy trên hai máy vật lý; cần thực hiện quy trình LAN ở trên. Kiểm thử giao diện Node là render/mô phỏng, không phải xác nhận camera/mic thực tế. Tab bị hệ điều hành treo quá timeout cũng sẽ Offline theo thiết kế; IP cho biết điểm kết nối quan sát được, không chứng minh người thực tế đang ngồi trước máy.

Kết quả tại thời điểm triển khai (04/11/2026): script LAN chạy thành công; lần chạy cuối ghi 57 checks PASS. Kiểm thử render danh sách kết nối có 10 checks PASS. Các bộ hồi quy Classroom, MeetingCall, ChatNotification, RoomLifecycle, Practice, ServerMonitor, ServerReliability, History, Recovery, WebGateway, EditSession, Integration, ScreenShareRelay, AssignmentModule và AssignmentProtocol đều chạy thành công với class và database kiểm thử riêng. Các kiểm tra frontend liên quan import, lớp học, media mô phỏng, điều hướng cuộc họp, chat, luyện code, form kỳ thi, danh sách phòng và screen audio cũng đã qua. Không coi đây là kiểm thử tải hoặc xác nhận media qua hai máy LAN.
