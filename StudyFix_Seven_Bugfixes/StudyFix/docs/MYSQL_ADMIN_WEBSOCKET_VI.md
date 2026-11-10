> TÀI LIỆU LỊCH SỬ: phiên bản hiện tại chỉ dùng SQLite. Các hướng dẫn MySQL bên dưới không còn áp dụng. Xem [SQLite-only](SQLITE_ONLY_VI.md).

# StudyFix — cấu hình MySQL, Teacher Admin và kiểm thử LAN

**Cập nhật luồng tham gia:** Student đã có thể thấy các lớp đang mở và gửi yêu cầu; Teacher chủ lớp nhận thông báo Chấp nhận/Từ chối. Xem [hướng dẫn xin vào lớp](CLASS_ADMISSION_VI.md). Thêm MSSV thủ công vẫn dùng được để cấp quyền trước.

## 1. Chế độ hoạt động

StudyFix giữ Java/TCP/HTTP, web HTML/CSS/ES Modules và module chấm hiện có. Không dùng Spring, React hoặc backend Node mới.

| Thành phần | Nơi lưu / truyền |
|---|---|
| Tài khoản, lớp, danh sách lớp, phiên, điểm danh ở chế độ Management | MySQL 8.0 trở lên |
| Code Master, nhánh, Merge, chat và lịch sử code | SQLite lõi hiện có |
| Ngân hàng bài, bài giao, bài nộp, kỳ thi và kết quả chấm | SQLite learning hiện có |
| Đăng nhập web | HTTP/HTTPS → TCP nội bộ → `Store.login()` → MySQL khi bật chế độ MySQL |
| Cập nhật bảng quản trị / kết nối | WebSocket `/presence`, cổng mặc định 8081; polling dự phòng |
| Lớp học, media, bài tập | Tái sử dụng API và WebRTC hiện có |

`TEACHER` là Admin **trong các lớp có `owner_id` của mình**. `STUDENT` là người học. Không có role `ADMIN` thứ ba. Teacher không tạo Teacher khác qua web, không xem/sửa lớp hoặc dữ liệu LAN của giảng viên khác.

Chế độ SQLite chưa gắn MySQL vẫn chạy các tính năng cũ; dashboard quản trị báo cần cấu hình MySQL. Khi một database lõi đã được gắn MySQL, thiếu biến môi trường MySQL sẽ làm khởi động bị từ chối, thay vì dùng bản sao tài khoản SQLite đã cũ để đăng nhập.

## 2. Kiểm tra MySQL trên máy server

Trong môi trường triển khai này đã quan sát thấy dịch vụ `MySQL80` chạy. Không cần cài lại nếu máy bạn vẫn có dịch vụ đó:

```powershell
Get-Service *mysql*
Test-NetConnection 127.0.0.1 -Port 3306
```

Nếu dịch vụ tồn tại nhưng dừng, mở PowerShell bằng quyền quản trị và chạy `Start-Service MySQL80` (thay tên nếu dịch vụ của bạn khác). Nếu chưa cài, cài MySQL Community Server và dùng MySQL Configurator để tạo Windows service, đặt mật khẩu root do bạn tự chọn. MySQL Workbench chỉ là giao diện quản lý; nó không thay thế MySQL Server.

Tham khảo chính thức: [cài MySQL trên Windows](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html), [MySQL Configurator](https://dev.mysql.com/doc/refman/8.4/en/mysql-configurator.html).

## 3. Tạo database và tài khoản ứng dụng

Mở MySQL Workbench, kết nối localhost bằng tài khoản quản trị MySQL của bạn. Không gửi mật khẩu MySQL vào chat.

Chạy file `server/db/mysql/00_create_database.sql`, hoặc:

```sql
CREATE DATABASE IF NOT EXISTS studyfix_management
  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

Trong **Server → Users and Privileges**, tạo tài khoản `studyfix_app` với mật khẩu riêng đủ mạnh, host `localhost`; cấp quyền trên đúng schema `studyfix_management`. Tài khoản ứng dụng cần SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX và REFERENCES để chạy migration. Không cần cấp quyền quản trị toàn MySQL.

Nếu MySQL phân biệt kết nối `127.0.0.1` với `localhost`, tạo thêm account host `127.0.0.1` với cùng username/password và cùng quyền schema. Có thể dùng các câu lệnh dưới bằng tài khoản quản trị; **thay chuỗi giữ chỗ bằng mật khẩu bạn tự chọn, không đưa mật khẩu thật vào source**:

```sql
CREATE USER 'studyfix_app'@'localhost' IDENTIFIED BY '<MAT_KHAU_RIENG_CUA_BAN>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON studyfix_management.* TO 'studyfix_app'@'localhost';
-- Chỉ thêm dòng sau nếu cần account theo IP loopback:
CREATE USER 'studyfix_app'@'127.0.0.1' IDENTIFIED BY '<CUNG_MAT_KHAU_RIENG_CUA_BAN>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON studyfix_management.* TO 'studyfix_app'@'127.0.0.1';
```

Nếu account đã tồn tại, không chạy lại `CREATE USER` hoặc xóa account; chỉnh quyền trong Workbench. Không dùng root làm tài khoản chạy ứng dụng.

Các bảng được khởi tạo/nâng cấp tự động từ `server/src/studyfix/db/mysql-schema.sql`, được đóng gói trong JAR. Không cần xóa database để nâng cấp.

## 4. Sao lưu và cấu hình

Đóng Server Manager trước khi sao lưu SQLite. Giữ cả thư mục `server/data` gồm database lõi, learning và các file WAL/SHM nếu còn. Sao lưu MySQL bằng công cụ export của Workbench. Không thay file SQLite cũ bằng database trống khi bật MySQL.

Từ thư mục `StudyFix_Seven_Bugfixes`:

```powershell
if (!(Test-Path .\StudyFix\server\.env)) {
    Copy-Item .\StudyFix\server\.env.example .\StudyFix\server\.env
}
notepad .\StudyFix\server\.env
```

Điền trên máy server:

```dotenv
STUDYFIX_MANAGEMENT_DB=mysql
STUDYFIX_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/studyfix_management?sslMode=DISABLED&allowPublicKeyRetrieval=true
STUDYFIX_MYSQL_USER=studyfix_app
STUDYFIX_MYSQL_PASSWORD=<mat-khau-tai-khoan-studyfix_app>
STUDYFIX_MYSQL_IMPORT_SQLITE=true
STUDYFIX_WS_ENABLED=true
STUDYFIX_WS_PORT=8081
```

Chuỗi giữ chỗ phải được thay bằng giá trị thật của bạn. `.env` đã được Git ignore; chỉ `.env.example` được chia sẻ. Launcher đọc KEY=value dạng literal, không thực thi nội dung file. Biến môi trường đã có trong process được ưu tiên hơn `.env`; tránh để cấu hình cũ trong PowerShell trỏ sang database khác.

URL mẫu ở trên dành cho MySQL ở **loopback cùng máy server**. Nếu MySQL nằm trên máy khác, cấu hình TLS Connector/J phù hợp thay vì áp dụng mẫu tắt TLS. Username/password được truyền riêng qua JDBC Properties, không đặt trong URL. Xem [cấu hình Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html).

### Có sẵn tài khoản SQLite

Lần đầu đặt `STUDYFIX_MYSQL_IMPORT_SQLITE=true`: nhập userId, username, MSSV và hash hiện có, không đổi mật khẩu. Hệ thống từ chối ghép hai tài khoản cùng username nhưng khác userId. Nếu gặp lỗi này, đối chiếu dữ liệu và bản sao lưu; không xóa DB để “sửa nhanh”.

Bước nhập cũng giữ các phiên/điểm danh SQLite cũ trong MySQL dưới trạng thái Offline, không phục hồi token đăng nhập cũ. Lịch sử chưa có IP theo lớp được để trống trường snapshot; IP raw vẫn giữ ở DB. Không tự suy đoán việc cấp quyền lớp hiện tại từ một lần tham gia trong quá khứ: nếu danh sách sinh viên sau import chưa có, Teacher thêm MSSV vào đúng lớp. Tài khoản và lịch sử cũ không bị xóa.

Sau khi nhập thành công, đặt `STUDYFIX_MYSQL_IMPORT_SQLITE=false`. MySQL trở thành nguồn xác thực duy nhất; bản sao trong SQLite chỉ phục vụ các khóa ngoại và nghiệp vụ code cũ. Thao tác khóa/đổi mật khẩu không được lấy lại giá trị từ bản sao SQLite.

MSSV cũ có thể đang bằng username, ví dụ tài khoản cũ `student1`. Không tự sinh MSSV thật. Tài khoản mới được cấp mã cụ thể từ form Teacher Admin.

### Database mới, chưa có Teacher

Điền thêm các biến sau trước lần chạy đầu; mật khẩu do bạn chọn, tối thiểu 12 ký tự:

```dotenv
STUDYFIX_BOOTSTRAP_TEACHER=<tai-khoan-teacher-dau-tien>
STUDYFIX_BOOTSTRAP_PASSWORD=<mat-khau-teacher-ban-tu-chon>
STUDYFIX_BOOTSTRAP_NAME=<ho-ten-giang-vien>
```

Bootstrap chỉ cấp Teacher khi MySQL chưa có Teacher; không reset tài khoản đã tồn tại. Không có mật khẩu Teacher/Student mặc định trong ứng dụng. Fixture mật khẩu trong `server/test/fixtures/accounts.tsv` chỉ dùng khi JVM kiểm thử được chỉ định file đó.

### Cấp Teacher thứ hai

Teacher trên web không có quyền tạo/nâng cấp Teacher. Người vận hành có credential MySQL có thể dùng terminal tương tác sau khi build server:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-management-cli.ps1 -Username teacher_b -DisplayName "Giảng viên B"
```

CLI yêu cầu nhập và xác nhận mật khẩu ẩn trong terminal, không nhận mật khẩu qua dòng lệnh. Dùng username mới; không tạo trùng tài khoản SQLite cũ trước quá trình import.

## 5. Chạy server

Các JAR JDBC/WebSocket đã được đặt trong `server/lib`. Để kiểm tra checksum hoặc tải lại file thiếu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\install-management-dependencies.ps1
```

Đóng Server Manager cũ, rồi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
```

Launcher dùng JDK/Ant hiện có và build Java. Lần đầu MySQL cần chạy migration; sai credential hoặc MySQL chưa sẵn sàng sẽ báo lỗi, không tự đổi sang SQLite. Không chạy hai launcher cùng lúc trên các cổng giống nhau.

Mở `http://localhost:8080/api/health` để kiểm tra trường `managementDatabase` là `mysql` và `webSocket.enabled` là `true`. Health cho biết cấu hình; các kiểm tra đăng nhập và thao tác dữ liệu bên dưới mới xác nhận đầy đủ database hoạt động.

Đăng nhập Teacher: vào dashboard quản trị. Đăng nhập Student: vào Không gian học tập, không có điều hướng Admin. Nhấn Ctrl+Shift+R nếu trình duyệt còn giữ module cũ.

## 6. Thao tác trên giao diện

1. **Tổng quan giảng viên:** số lớp, sinh viên thuộc lớp, Online/Offline hệ thống, sinh viên đang trong lớp, phòng có người và hoạt động giảng dạy.
2. **Lớp học của tôi:** tạo lớp, sửa tên, đóng/mở, xem/thêm/gỡ thành viên. Đóng lớp giữ code/lịch sử; xóa theo quy tắc chủ phòng và điều kiện phòng hiện có, không xóa cứng lịch sử.
3. **Sinh viên:** tạo tài khoản Student, xem tài khoản và lớp. Khi tài khoản chỉ thuộc phạm vi quản lý thích hợp, có thể khóa/mở và đổi mật khẩu với xác nhận. Tài khoản dùng chung giữa các giảng viên không bị một Teacher tự thay đổi toàn cục.
4. **Kết nối LAN:** mỗi dòng là một kết nối của sinh viên từng tham gia lớp của mình. Hiển thị IP, thời gian, mã theo dõi không có quyền xác thực; phân biệt đăng nhập hệ thống và đang có mặt trong lớp. “Ngắt khỏi lớp” chỉ tách kết nối đó khỏi lớp, không đăng xuất tất cả thiết bị.
5. **Điểm danh:** giữ từng lượt vào–ra, kể cả rời rồi tham gia lại bằng cùng phiên. Thời lượng tính theo heartbeat được xác nhận; không coi việc đăng nhập là tham gia lớp.
6. **Bài tập & tiến độ:** chọn lớp để mở module ngân hàng/giao bài/bài nộp/chấm hiện có; không có Online Judge thứ hai. Nếu đang là thành viên của lớp khác, rời phòng đó trước khi chuyển lớp.

Các bảng có tìm kiếm, lọc trạng thái/lớp khi phù hợp và phân trang 10 dòng. Danh sách phòng học cũ giữ quy tắc 5 phòng/trang. Sidebar mobile giữ trạng thái mở khi dữ liệu cập nhật.

Tạo Student xong chưa tự cấp quyền vào mọi lớp. Student thấy các lớp đang mở và bấm Xin vào lớp; Teacher chủ lớp duyệt thì Student được vào. Có thể cấp quyền trước qua Lớp học của tôi → Thành viên → Thêm bằng MSSV. Đóng/gỡ lớp không cấm sinh viên luyện code cá nhân.

## 7. WebSocket, heartbeat và IP

- WebSocket dùng thư viện Java-WebSocket và cùng token đăng nhập hiện có, gửi token trong frame AUTH chứ không đặt trong URL. Không tạo hệ thống đăng nhập thứ hai.
- Frontend gửi heartbeat ứng dụng mỗi 10 giây; server đẩy snapshot thay đổi khoảng 2 giây. Polling hiện có tiếp tục phục vụ lớp học và dự phòng khi socket gián đoạn.
- Timeout web mặc định 120 giây không nhận request/heartbeat xác thực; tác vụ dọn chạy mỗi giây. Đăng xuất thu hồi đúng phiên, không ảnh hưởng phiên khác của cùng tài khoản.
- IP lấy từ peer HTTP/WebSocket do server quan sát, không nhận IP do frontend gửi. Header `Forwarded`/`X-Forwarded-For` không được tin tưởng vì chưa có cấu hình proxy tin cậy. Qua reverse proxy, IP quan sát có thể là IP proxy.
- Đăng nhập bằng localhost có thể hiện `127.0.0.1` hoặc IPv6 `::1`; đó không phải lỗi. Muốn kiểm tra IP LAN của máy khác phải truy cập từ máy đó.
- Kết nối lại trong thời gian phiên còn hiệu lực dùng lại token. Hết hạn thì đăng nhập lại, tạo phiên mới; lịch sử cũ giữ Offline. Restart chuyển phiên còn Online về Offline, không khôi phục Online giả.
- “Hoạt động cuối” là hoạt động mạng đã xác thực, không phải thời điểm gõ phím. Tab bị hệ điều hành treo quá timeout cũng có thể Offline.

## 8. Kiểm thử bằng hai máy cùng LAN

**Máy server:** chạy `ipconfig`, lấy IPv4 của card mạng đang dùng. Ví dụ nếu vẫn là `192.168.20.251`, máy sinh viên mở `http://192.168.20.251:8080`. Trên máy sinh viên không dùng localhost để trỏ tới máy server.

Cho phép Java/cổng web 8080 và WebSocket 8081 trong Windows Firewall của mạng phù hợp. Không cần mở MySQL 3306 cho máy sinh viên. Kiểm tra từ máy sinh viên:

```powershell
Test-NetConnection 192.168.20.251 -Port 8080
Test-NetConnection 192.168.20.251 -Port 8081
```

Teacher tạo lớp và tài khoản Student, thêm MSSV vào lớp. Máy thứ hai đăng nhập Student, vào lớp. Teacher mở Kết nối LAN: IP phải khớp IP quan sát thực tế của máy thứ hai. Thử lần lượt:

| Thử nghiệm | Mong đợi |
|---|---|
| Student mở API `/api/admin` bằng token của mình | 403, không có dữ liệu riêng |
| Teacher B chọn/sửa classId của Teacher A | 403; không có lớp đó trên dashboard/WS của B |
| Hai trình duyệt đăng nhập cùng Student | Hai kết nối, một danh tính người dùng |
| Đăng xuất một trình duyệt | Chỉ phiên đó Offline |
| Ngắt khỏi lớp một kết nối | Kết nối đó rời lớp; phiên hệ thống/thiết bị khác giữ nguyên |
| Tắt Wi-Fi máy Student | Offline sau timeout và thời gian đồng bộ |
| Bật mạng, đăng nhập lại sau timeout | Phiên mới Online; phiên cũ vẫn Offline |
| Rời và vào lại cùng phiên | Có nhiều lượt điểm danh, không mất lượt trước |
| Đóng lớp | Người tham gia rời lớp, không vào lại cho tới khi mở |
| Restart server | Tài khoản/lớp/code còn; phiên cũ Offline |
| Giảng bài → nhánh → kiểm tra → Merge, chat, bài tập | Vẫn dùng luồng cũ |

Để dùng camera/mic/chia sẻ màn hình qua LAN, tiếp tục cấu hình HTTPS bằng `STUDYFIX_WEB_KEYSTORE` và `STUDYFIX_WEB_KEYSTORE_PASSWORD` như hướng dẫn media trong README. WebSocket dùng cùng keystore để phục vụ WSS. Trình duyệt cần tin cậy chứng chỉ. Không đổi HTTP thành HTTPS trong URL khi server chưa cấu hình TLS.

## 9. Kiểm thử tự động và bằng chứng

Từ thư mục gốc:

```powershell
# Không dùng database đang vận hành; tạo MySQL instance riêng bằng mysqld có sẵn:
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\test-mysql-isolated.ps1
# Nếu MySQL binary nằm ở nơi khác:
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\test-mysql-isolated.ps1 -MySqlBin "C:\Program Files\MySQL\MySQL Server 8.4\bin"
node .\StudyFix\web-client\tests\check_admin.mjs
node .\StudyFix\web-client\tests\check_teacher_roles.mjs
node .\StudyFix\web-client\tests\check_presence_socket.mjs
```

Script MySQL dùng datadir dưới `server/build/mysql-isolated-*`, cổng ngẫu nhiên, credential sinh ngẫu nhiên và chỉ dừng tiến trình do chính nó khởi chạy. Không thay đổi dịch vụ MySQL80, không xóa DB vận hành. Test chạy trên database `_test` riêng; guard kiểm tra tên trước migration. Có kiểm tra nâng cấp schema v1 giữ nguyên hash và điểm danh.

Để chạy hồi quy Ant mà không ghi đè JAR vận hành, từ `StudyFix/server`:

```powershell
. .\resolve-tools.ps1
& $ant -Dbuild.dir=build/teacher-admin-ant -Ddist.dir=build/teacher-admin-package integration-test
```

Kiểm thử browser thật là tùy chọn, dùng Playwright với Chrome headless. Đặt `STUDYFIX_BROWSER_TEST_NODE` trỏ tới node.exe và `STUDYFIX_BROWSER_TEST_MODULES` trỏ tới thư mục node_modules có Playwright rồi chạy script MySQL tạm. Node chỉ phục vụ kiểm thử, không phải backend của ứng dụng. Screenshot QA nằm trong `server/build/teacher-admin-browser-qa`.

Xem [báo cáo nâng cấp](TEACHER_ADMIN_UPGRADE_REPORT_VI.md) để biết kết quả thực tế, file thay đổi và giới hạn kiểm chứng. Kiểm thử localhost/Chrome trên một máy không thay thế việc kiểm thử hai máy LAN vật lý và media thực tế.
