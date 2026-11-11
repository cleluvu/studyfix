# StudyFix

**Bài sửa lỗi từ ngân hàng trong phòng học:** giáo viên chọn code chuẩn, tự tạo lỗi rồi lưu vào Danh sách bài lỗi để chuẩn bị trước buổi học; chọn nhanh bài đã lưu để giao vào Master/nhánh học sinh. Xem [hướng dẫn sử dụng và kiểm thử](docs/TAO_BAI_SUA_LOI_TU_NGAN_HANG_VI.md).


**Kỳ thi trực tuyến:** giáo viên tạo kỳ thi, chọn học sinh và lên lịch; học sinh vào thi trên web. Danh sách học sinh nằm trong chi tiết kỳ thi; tiếp tục dùng Giám sát và Chấm thi & kết quả. Xem [luồng kỳ thi và hướng dẫn cập nhật backend](docs/KET_QUA_TRIEN_KHAI_TO_CHUC_THI_VI.md).

StudyFix là ứng dụng học và thực hành Java theo nhóm. Giáo viên có thể tạo phòng, giảng trên code chung, tách bài làm riêng cho sinh viên, theo dõi kết quả rồi chọn một lời giải làm code chung mới. Người dùng cũng có thể luyện code cá nhân, làm bài được giao, tham gia cuộc thi, chat và chia sẻ màn hình.

Server viết bằng Java. Cấu hình hiện tại dùng SQLite cho tài khoản/lớp/phiên/điểm danh, lịch sử code và ngân hàng/chấm bài. Giao diện chính chạy trên trình duyệt HTML/CSS/JavaScript; Swing dùng cho Server Manager phía máy chủ. Chi tiết chuyển đổi và ngân hàng 100 bài/12 Week: [hướng dẫn SQLite](docs/SQLITE_VA_NGAN_HANG_100_BAI_VI.md).

**Teacher Admin + WebSocket** tiếp tục hoạt động trên SQLite. Tài khoản cũ được giữ, credential bootstrap lấy từ biến môi trường; fixture tài khoản chỉ dùng cho test. Hệ thống chỉ hỗ trợ SQLite; đã gỡ driver, cấu hình và nhánh chạy MySQL. Xem [báo cáo SQLite-only](docs/SQLITE_ONLY_VI.md).

**Problem Builder:** đã có luồng tạo Java đủ bảy bước, testcase/import/generator, cấu hình điểm, kiểm định Docker, review/phát hành snapshot và tạo phiên bản mới. Xem [hướng dẫn bước 4–7, ví dụ và giới hạn runner](docs/PROBLEM_BUILDER_4_7_HUONG_DAN_VI.md). Method/Class dùng trusted Java harness; không mặc định coi đó là JUnit.

Mở Builder bằng nút **Tạo bài tập** trong **Tất cả bài tập**: wizard hiện trong hộp thoại, không có trang hay mục sidebar riêng. Đóng lưu nháp và trở về danh sách.

Xem [báo cáo nâng cấp Teacher Admin](docs/TEACHER_ADMIN_UPGRADE_REPORT_VI.md) để biết kiến trúc, file thay đổi, phân quyền theo lớp, migration và kết quả kiểm thử thực tế.

Luồng tham gia mới: [Student xin vào lớp, Teacher nhận thông báo và duyệt](docs/CLASS_ADMISSION_VI.md).

Kiểm tra lập trình: [Exam Mode, hạn chế Paste và giám sát rời trang theo kỳ thi](docs/EXAM_MODE_VI.md).

## Mục lục

- [1. Cấu trúc dự án](#cau-truc)
- [2. Chuẩn bị môi trường](#moi-truong)
- [3. Chạy ứng dụng](#chay-ung-dung)
- [4. Hướng dẫn sử dụng](#su-dung)
- [5. Mic, camera, chia sẻ màn hình và chat](#media-chat)
- [6. Kiến trúc mã nguồn](#kien-truc)
- [7. API HTTP](#api)
- [8. Giao thức TCP](#tcp)
- [9. Chấm bài và bảo vệ dữ liệu](#cham-bai)
- [10. Database, sao lưu và khôi phục](#du-lieu)
- [11. Kiểm thử](#kiem-thu)
- [12. Lỗi thường gặp và giới hạn](#xu-ly-loi)
- [13. Thư viện đi kèm](#thu-vien)

<a id="cau-truc"></a>
## 1. Cấu trúc dự án

```text
StudyFix/
├── README.md                     Hướng dẫn chung
├── server/
│   ├── src/studyfix/              Mã nguồn server và các module
│   ├── test/                     Kiểm thử Java và script kiểm chứng
│   ├── data/                     Database đang sử dụng
│   ├── lib/                      Thư viện SQLite JDBC
│   ├── nbproject/                Cấu hình Ant/NetBeans
│   ├── build.xml                 Build và chạy kiểm thử server
│   ├── manifest.mf               Điểm chạy của JAR
│   └── *.ps1                     Script chạy và kiểm thử
├── client/
│   ├── src/main/java/            Mã nguồn client Swing
│   ├── src/main/resources/       Cấu hình kết nối mặc định
│   ├── src/test/java/            Kiểm thử client
│   ├── pom.xml                   Cấu hình Maven
│   └── *.ps1                     Script build, chạy và kiểm thử
└── web-client/
    ├── static/                   HTML, CSS và JavaScript phục vụ trình duyệt
    └── tests/                    Kiểm thử frontend bằng Node.js
```

Các thư mục `server/build/`, `server/build-launcher/`, `server/dist/` và `client/target/` là kết quả build hoặc kiểm thử, có thể được tạo lại. Không sửa trực tiếp file `.class` hoặc JAR đã build.

Các file XML, properties, manifest, script PowerShell, thư viện JAR, HTML/CSS và database vẫn cần được giữ. Chúng phục vụ cấu hình, giao diện, build hoặc lưu dữ liệu dù không phải toàn bộ đều chứa logic nghiệp vụ. `server/nbproject/` được `build.xml` sử dụng, không chỉ là cấu hình hiển thị của IDE.

<a id="moi-truong"></a>
## 2. Chuẩn bị môi trường

| Công cụ | Khi nào cần |
|---|---|
| JDK 26, có cả `java` và `javac` | Build và chạy server theo cấu hình hiện tại |
| Apache Ant | Build server và chạy test Java |
| Docker Desktop hoặc Docker Engine | Chạy và chấm code Java trong sandbox |
| Chrome/Edge hoặc trình duyệt hỗ trợ ES Modules/WebRTC | Dùng giao diện web |
| Node.js | Chạy kiểm thử frontend; không bắt buộc khi chỉ dùng ứng dụng |

Client Swing được build với `--release 17`; có thể dùng cùng JDK 26 của server để build và chạy. Frontend dùng JavaScript thuần và ES Modules, không cần `npm install`, Vite hay một server Node riêng.

Mở PowerShell tại thư mục `StudyFix`. Nếu cần cấu hình công cụ thủ công, thay đường dẫn bên dưới theo máy:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:ANT_HOME = 'C:\tools\apache-ant'
$env:Path = "$env:JAVA_HOME\bin;$env:ANT_HOME\bin;$env:Path"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
java -version
javac -version
ant -version
```

Script server có hỗ trợ tìm Java/Ant từ môi trường và một số vị trí cài đặt thông dụng. Cách rõ ràng nhất là đặt đúng `JAVA_HOME` và thêm Ant vào `PATH`.

Chuẩn bị Docker nếu muốn chấm bài thật:

```powershell
docker info
docker pull eclipse-temurin:21-jdk
```

Lệnh pull cần mạng. Nếu máy thực hành không có mạng, chuẩn bị hoặc nhập image trước. Bộ chấm dùng `--pull=never`, không tự tải image khi nhận bài nộp.

<a id="chay-ung-dung"></a>
## 3. Chạy ứng dụng

### 3.1. Server Manager và giao diện web

Từ thư mục `StudyFix`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1
```

Script build server, mở cửa sổ quản lý và khởi động các dịch vụ trong cùng JVM:

| Cổng mặc định | Dịch vụ |
|---|---|
| TCP 5000 | Đăng nhập, phòng, chat và code cộng tác |
| TCP 5001 | Relay chia sẻ màn hình cho client Swing |
| TCP 5002 | Bài tập, nộp bài, chấm bài và cuộc thi |
| HTTP 8080 | Giao diện web và API |

Mở `http://localhost:8080`. Server Manager hiển thị kết nối, phòng, code, lịch sử, log và trạng thái các dịch vụ. Giữ chương trình đang chạy trong lúc sử dụng. Khi dừng, dùng nút **Dừng toàn bộ dịch vụ**, chờ dừng rồi đóng cửa sổ.

Muốn chạy server dạng console, từ thư mục `StudyFix` dùng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-with-assignments.ps1
```

Chọn một trong hai cách chạy, không chạy đồng thời trên cùng cổng và database. `ant run` chỉ chạy phần server lõi, không thay thế launcher đầy đủ khi cần module bài tập và web.

### 3.2. Client Java Swing

Mở PowerShell khác tại `StudyFix`. Đóng các cửa sổ client cũ trước khi build:

```powershell
cd .\client
.\build.ps1
.\run-client-only.ps1
```

Để mở thêm client, mở terminal mới tại `StudyFix/client` rồi chạy lại `run-client-only.ps1`. Mỗi cửa sổ là một tiến trình, có thể đăng nhập tài khoản khác nhau.

`run.ps1` vừa build vừa chạy. Không dùng script này hoặc `build.ps1` khi JAR client đang được một cửa sổ khác sử dụng. `build.ps1` tạo lại toàn bộ `client/target/`.

Cấu hình mặc định nằm trong `client/src/main/resources/studyfix-client.properties`: host `127.0.0.1`, control port `5000`, screen port `5001`. Có thể ghi đè bằng JVM properties `studyfix.host`, `studyfix.port`, `studyfix.screen.port`; module bài tập dùng `studyfix.assignment.port`.

### 3.3. Tài khoản demo

Server tạo các tài khoản này khi khởi tạo database lần đầu, không ghi đè tài khoản đã có:

| Tài khoản | Mật khẩu demo | Vai trò |
|---|---|---|
| `teacher` | `Teacher123!` | Giáo viên |
| `teacher2` | `Teacher234!` | Giáo viên |
| `student1` | `Student123!` | Sinh viên |
| `student2` | `Student234!` | Sinh viên |

Nếu đang dùng database có dữ liệu riêng, đăng nhập bằng tài khoản đã tồn tại. Không xóa database để tạo lại tài khoản demo. Quyền được xác định từ database ở server, không theo vai trò tự khai của client.

### 3.4. Kết nối trong LAN và HTTPS

Máy sinh viên dùng IP LAN của máy server, ví dụ `http://192.168.1.10:8080`. Địa chỉ `localhost` trên máy sinh viên chỉ đến chính máy đó. Cho phép các cổng cần dùng qua Windows Firewall trên mạng thực hành phù hợp.

Mic, camera và quay màn hình cần ngữ cảnh an toàn của trình duyệt. `localhost` có thể dùng HTTP để phát triển; truy cập bằng IP/hostname LAN nên cấu hình HTTPS với chứng chỉ được tin cậy. Từ thư mục `StudyFix`:

```powershell
$env:STUDYFIX_WEB_PORT = '8443'
$env:STUDYFIX_WEB_KEYSTORE = 'C:\certs\studyfix.p12'
$env:STUDYFIX_WEB_KEYSTORE_PASSWORD = '<mat-khau-keystore>'
.\server\run-server-manager.ps1
```

Keystore dùng PKCS12. SAN của chứng chỉ phải khớp IP/hostname truy cập và trình duyệt phải tin cậy chứng chỉ. Có thể dùng reverse proxy TLS thay thế. Không đưa mật khẩu keystore thật vào mã nguồn. HTTP và TCP thường không mã hóa dữ liệu; chỉ dùng cấu hình demo trên localhost hoặc LAN tin cậy.

<a id="su-dung"></a>
## 4. Hướng dẫn sử dụng

### 4.1. Tạo, tìm, vào và xóa phòng

Giáo viên tạo phòng tại **Tổng quan** bằng mã tự chọn, ví dụ `Lớp Java A`. Sinh viên chọn phòng hoặc nhập cùng mã để tham gia. Mỗi phiên chỉ tham gia một phòng tại một thời điểm.

Mã phòng dài 1–80 ký tự Unicode, được có tiếng Việt, dấu câu và khoảng trắng bên trong. Hệ thống bỏ khoảng trắng ngoài cùng, từ chối xuống dòng/ký tự điều khiển và kiểm tra trùng theo Unicode NFC, không phân biệt hoa/thường. Tìm kiếm phòng bỏ qua dấu tiếng Việt và hoa/thường, lọc toàn bộ danh sách trước khi phân trang.

Danh sách có tối đa 5 phòng/trang, gồm cả phòng trống. Rời hết thành viên hoặc restart server không tự xóa phòng. Chỉ giáo viên chủ phòng được xóa, kể cả khi đang ở ngoài phòng. Sau khi xác nhận xóa, các thành viên rời phòng và dừng media nhưng vẫn giữ đăng nhập. Database giữ lịch sử cũ; tạo lại cùng mã sẽ có ID phòng mới.

Phân biệt các thao tác:

| Thao tác | Kết quả |
|---|---|
| **Rời cuộc họp** trong workspace | Dừng media, về Tổng quan, vẫn tham gia phòng |
| **Rời phòng** trong danh sách | Bỏ tham gia, có thể vào phòng khác |
| **Ngắt cuộc gọi** | Dừng mic/camera và kết nối gọi, vẫn ở workspace |
| **Xóa phòng** của chủ phòng | Đánh dấu phòng đã xóa, ngăn tham gia lại |

Tải lại trang chỉ khôi phục workspace sau khi server xác nhận tài khoản và phòng; không tự bật lại mic/camera. Đóng tab có khoảng đệm 8 giây để hỗ trợ tải lại. Phiên web không còn request sẽ được dọn sau khoảng 2 phút, kiểm tra định kỳ 15 giây; việc này không xóa phòng.

### 4.2. Phòng học Master và nhánh cá nhân

Luồng này dùng trên giao diện web. Client Swing vẫn có luồng sửa code chung theo lượt.

1. Giáo viên và sinh viên vào cùng phòng.
2. Giáo viên chọn **Bắt đầu giảng bài**, chọn bài `DEBUG_COLLAB` và dùng code mẫu.
3. Giáo viên giảng, chỉnh sửa và **Lưu Master**. Sinh viên xem Master.
4. Khi có sinh viên trong phòng, giáo viên **Tách nhánh cho cả lớp** từ đúng phiên bản Master.
5. Sinh viên sửa nhánh riêng, lưu hoặc để tự lưu sau khoảng 1,2 giây; dùng **Chạy kiểm tra** để chấm bản đã lưu.
6. Giáo viên theo dõi bài làm trong lưới, mở code và kết quả của từng sinh viên.
7. Giáo viên **Dừng sửa và chọn lời giải**, xem một nhánh rồi xác nhận **Chọn nhánh này làm Master**. Có thể cho sinh viên sửa tiếp.
8. Sau Merge, cả lớp quay lại giảng trên Master mới; có thể mở vòng tiếp theo hoặc kết thúc phiên.

Các trạng thái chính: `WAITING → LECTURE → BRANCHING → MERGING → LECTURE`. Có thể quay từ `MERGING` về `BRANCHING`, hoặc kết thúc bằng `ENDED` rồi bắt đầu phiên mới.

Nhánh gắn với phòng, vòng và `userId`, nên đăng nhập lại cùng tài khoản có thể lấy bản đã lưu. Sinh viên vào muộn trong `BRANCHING` nhận nhánh từ Master của vòng đó; vào trong `MERGING` cần chờ vòng sau. Học sinh chỉ xem nhánh và lịch sử của mình; giáo viên chủ phòng xem cả lớp. Lưới giáo viên hiển thị tối đa 25 bài/trang và bản xem trước 14 dòng.

Lưu và Merge đều kiểm tra phiên bản để tránh ghi đè khi có thao tác đồng thời. Merge chọn toàn bộ snapshot của một nhánh làm Master, không tự ghép nhiều nhánh như Git và không bắt buộc nhánh phải AC. Kết quả chấm cũ được đánh dấu nếu phiên bản code hiện tại đã khác.

Nháp chưa lưu nằm trong `sessionStorage` của tab, có thể mất khi đóng tab hoặc xóa dữ liệu trình duyệt. Bản đã lưu trên server và snapshot chấm được lưu bền vững. Sau restart cần đăng nhập và vào lại phòng. Khi đã dùng phiên học mới, server chặn học sinh sửa Master qua các lệnh TCP xin lượt cũ.

### 4.3. Luyện code cá nhân

Giáo viên và sinh viên đều mở **Luyện code** mà không cần tham gia phòng hay được giao bài. Danh sách gồm bài viết từ đầu và sửa lỗi, có tìm kiếm, bộ lọc và 10 bài/trang.

Chọn một bài để xem đề, ví dụ, code mẫu và editor Java. **Chạy thử** chỉ chạy test công khai, không tạo phiên bản nộp chính thức. **Nộp bài** lưu snapshot rồi chấm toàn bộ test. **Nạp code từ file** nhận `.java`/`.txt` tối đa 32.000 byte, xác nhận trước khi thay nội dung và không tự nộp.

Nháp được giữ theo tài khoản và bài trong `localStorage`; bài đang mở được giữ theo tab. Lịch sử riêng của từng tài khoản/bài có 10 phiên bản/trang, mới nhất trước. Nộp tiếp tạo phiên bản mới, không ghi đè snapshot cũ. Giáo viên không được xem code luyện riêng của tài khoản khác chỉ nhờ vai trò giáo viên.

Bài đã từng AC vẫn được đánh dấu **Đã AC** dù lần nộp sau sai; mỗi phiên bản giữ verdict riêng. Lỗi Docker/dịch vụ được phân biệt với lỗi bài làm. Mã yêu cầu chống trùng giúp gửi lại cùng bài nộp không tạo thêm phiên bản ngoài ý muốn.

### 4.4. Bài được giao và cuộc thi

Giáo viên chủ phòng xem ngân hàng đề, giao bài cho cả phòng hoặc từng sinh viên, đặt hạn và theo dõi kết quả. Sinh viên làm bài riêng hoặc nộp snapshot code chung theo loại bài được phép. Danh sách người nhận được chụp tại thời điểm giao để người vào muộn không làm thay đổi báo cáo cũ.

Với bài sửa lỗi chung, nạp/khôi phục starter cần xác nhận đúng bài, đúng phiên bản Master và kiểm tra bản nháp/quyền sửa đang hoạt động. Chạy thử riêng của giáo viên không tính vào thống kê sinh viên hay điểm cuộc thi.

Cuộc thi được tạo trực tiếp từ ID bài trong ngân hàng đề, không bắt buộc giao bài trước. Thời gian bắt đầu/kết thúc do server kiểm tra. Loại `INDIVIDUAL` chỉ nhận bài `BUILD_FROM_SCRATCH`; `TEAM_COLLAB` chưa được hỗ trợ. Bảng xếp hạng trên web chỉ dành cho giáo viên, có kiểm tra quyền tại API. Kết quả đạt luôn xét theo đúng người dùng và đúng bài/ngữ cảnh.

<a id="media-chat"></a>
## 5. Mic, camera, chia sẻ màn hình và chat

### Cuộc gọi và màn hình trên trình duyệt

Chọn **Tham gia nghe** để nhận âm thanh mà không xin quyền mic/camera. Bật mic hoặc camera khi cần và đồng ý cấp quyền của trình duyệt. Tắt camera dừng track camera; ngắt cuộc gọi dừng các track và kết nối tương ứng. Nếu âm thanh bị chặn tự phát, nhấn nút bật âm thanh nhận.

Giáo viên và sinh viên đều có thể chia sẻ màn hình, mỗi phòng chỉ một người chia sẻ tại một thời điểm. Khi chia sẻ YouTube có âm thanh trên Chrome/Edge desktop, chọn **Tab trình duyệt**, chọn tab YouTube và bật **Chia sẻ âm thanh tab**. Chia sẻ cửa sổ hoặc toàn màn hình có thể không lấy được âm thanh tùy trình duyệt/hệ điều hành. Người nhận có thể cần nhấn nút phát âm thanh chia sẻ.

Âm thanh chia sẻ tab và microphone là hai nguồn riêng: tắt mic không tắt âm thanh tab, tắt camera không tắt màn hình chia sẻ. Chuyển trang trong ứng dụng khi đang gọi có thanh điều khiển nổi; rời cuộc họp/phòng hoặc đăng xuất sẽ dọn media.

Cuộc gọi dùng WebRTC mesh trực tiếp trong LAN, giới hạn 16 thiết bị media/phòng. Chưa có SFU/TURN hoặc bảo đảm kết nối qua NAT/mạng khác nhau. Chưa có đo tải 16 camera thật. Phía trả lời dùng transceiver của offer nhận vào; polling tín hiệu được xử lý tuần tự để tránh áp dụng offer trùng.

### Chia sẻ màn hình Swing

Client Swing chụp màn hình, nén JPEG rồi gửi qua relay TCP 5001. Lấy vé một lần từ kênh điều khiển sau khi xác thực và kiểm tra phòng. Vé có hạn 30 giây, gắn với phiên, phòng và chế độ `PUBLISH`/`VIEW`. Không dùng `sessionId` công khai làm vé.

Relay giới hạn frame 2 MiB và tối đa 10 frame/giây; viewer chậm chỉ cần ảnh mới nhất để không chặn người chia sẻ. Rời phòng, mất kết nối hoặc dừng server sẽ đóng luồng liên quan. Relay Swing và WebRTC trên trình duyệt là hai đường truyền riêng, chưa có cầu nối giữa chúng.

### Chat và tin chưa đọc

Chat được ghi database trước khi phát tới phòng. Giao diện tải 80 tin gần nhất nhưng server đếm tin chưa đọc trên toàn bộ lịch sử. Tin của chính tài khoản và thông báo vận hành không làm tăng số chưa đọc; phân biệt người gửi bằng `userId`.

Chỉ đánh dấu đã đọc khi tab có focus, đang ở phòng học, chat mở, cuộn tới cuối và không bị hộp thoại che. Mốc đã đọc lưu theo tài khoản/phòng trong trình duyệt, còn sau tải lại nhưng chưa đồng bộ giữa các máy. Đây không phải biên nhận đã đọc cho người gửi. Khi đang đọc tin cũ, tin mới không kéo mất vị trí cuộn.

<a id="kien-truc"></a>
## 6. Kiến trúc mã nguồn

### 6.1. Server Java

| Lớp hoặc package | Trách nhiệm |
|---|---|
| `Studyfix`, `ServerAdmin` | Điểm chạy và giao diện quản lý; I/O chạy ngoài luồng giao diện Swing |
| `Server`, `ClientHandler`, `Protocol` | Kết nối TCP, đăng nhập, nhận/gửi lệnh, heartbeat và vòng đời kết nối |
| `Identity`, `Passwords` | Danh tính và kiểm tra mật khẩu |
| `Room`, `EditSession` | Thành viên, quyền sửa, bản nháp, Master và kiểm tra phiên bản |
| `Store`, `CodeRevision`, `CodeDiff` | Transaction SQLite, lịch sử bất biến và phần khác biệt của code |
| `StudyFixGateway` | Cầu nối để module khác dùng danh tính, phòng, code chung và sự kiện rời phòng |
| `ClassroomRepository`, `ClassroomService` | Lưu phiên học, nhánh, chat, kết quả kiểm tra và xử lý Fork/Merge |
| `WebGatewayServer` | HTTP/HTTPS API, phiên web, phục vụ file tĩnh và tín hiệu chia sẻ màn hình |
| `MeetingCalls` | Kiểm tra quyền và quản lý tín hiệu WebRTC cuộc gọi |
| `screenshare/` | Giao thức nhị phân và relay màn hình Swing |
| `assignment/` | Ngân hàng đề, giao bài, luyện tập, cuộc thi, queue chấm và Docker sandbox |

Quyền do server quyết định từ phiên đã đăng nhập. Dữ liệu code được commit trước khi cập nhật RAM và phát sự kiện thành công. Khóa theo phòng bảo vệ thao tác đồng thời; không chờ truyền mạng trong khi giữ khóa phòng. Mỗi socket có reader/writer riêng và hàng đợi phản hồi giới hạn 64 mục; khi đầy sẽ ngắt client thay vì âm thầm làm mất sự kiện.

Mặc định phần điều khiển giới hạn 128 kết nối, tối đa 256 worker, heartbeat 5 giây và timeout khoảng 20 giây. Lịch sử code được lưu theo tác giả, thời điểm, phiên bản trước/sau, diff và toàn bộ snapshot. Xem bản cũ không đổi code hiện tại. Timestamp lưu theo UTC và lịch sử hiển thị theo giờ Việt Nam.

Diff giữ nguyên LF/CRLF/CR và dòng cuối không xuống dòng. Thuật toán bỏ phần đầu/cuối giống nhau, dùng LCS với tối đa 1.000.000 ô; vùng lớn dùng Myers với giới hạn truy vết khoảng 16 MB và 2.000.000 bước. Vượt giới hạn thì hiển thị thay thế toàn đoạn giữa thay vì cấp phát không giới hạn. Lịch sử hiện được nạp toàn bộ vào RAM, chưa có cơ chế phân trang database/dọn revision cũ.

### 6.2. Client Swing

Mã trong `client/src/main/java/vn/studyfix/client/` được chia thành `config`, `net`, `protocol`, `model`, `service`, `controller`, `ui`, `screen`, `integration` và `exercise`.

`TcpControlConnection` giữ một kết nối điều khiển; các service gửi lệnh qua kết nối chung. `HistoryFrameAssembler` ghép lịch sử nhiều frame. `RoomController` cập nhật trạng thái giao diện từ sự kiện server, không tự cấp quyền. `ClientModule` và `ClientModuleContext` cung cấp điểm tích hợp tab. `ExerciseModule` là module bài tập đang được dùng, có `AssignmentConnection` riêng đến cổng 5002, không tranh luồng đọc cổng 5000. `ExerciseIntegrationPanel` là lớp giao diện tạm cũ còn giữ trong mã nguồn.

### 6.3. Frontend JavaScript

Entry là `web-client/static/index.html`, nạp `app.js` bằng `type="module"`. Server ưu tiên `web-client/dist/` nếu có `index.html`, nếu không dùng `web-client/static/`. Bản hiện tại chạy trực tiếp từ file tĩnh.

| Thư mục/file trong `static/` | Vai trò |
|---|---|
| `app.js` | Đăng ký trang, nạp xử lý sự kiện, khởi động ứng dụng |
| `core/state.js`, `core/dom.js` | Trạng thái dùng chung và tham chiếu DOM |
| `core/api.js` | Gọi HTTP, lệnh phòng và module bài tập |
| `core/router.js` | Đăng ký/tra hàm hiển thị từng trang |
| `core/polling.js`, `core/lifecycle.js` | Đồng bộ dữ liệu, điều hướng và khởi tạo |
| `core/events.js` | Nhận click/submit/input/change rồi gọi chức năng tương ứng |
| `ui/` | Layout, render, nút/thẻ, toast và hộp thoại dùng chung |
| `shared/` | Định dạng, mã ngẫu nhiên và SHA-256 |
| `features/auth/`, `features/room/` | Đăng nhập và thao tác phòng |
| `features/classroom/` | Master/nhánh, giao diện cuộc họp, cuộc gọi và tin chưa đọc |
| `features/screen-share/` | Quay màn hình và kết nối WebRTC chia sẻ |
| `features/exercises/`, `features/contests/` | Bài tập, luyện cá nhân và cuộc thi |

Để tránh import vòng, các trang đăng ký qua `registerPage()`. `api.js` phát sự kiện `studyfix:unauthorized` để auth xử lý đăng xuất khi nhận 401. Polling gọi các callback được đăng ký bởi auth và screen-share thay vì import ngược các module đó.

Khi thêm chức năng, đặt code vào `features/`, dùng hàm gọi API sẵn trong `core/api.js`, đăng ký trang nếu cần và nối thao tác trong `core/events.js`. Giữ kiểm tra phân quyền ở server. Chạy kiểm tra import sau khi đổi cấu trúc module. CSS nằm trong `app.css` và `meeting.css`.

<a id="api"></a>
## 7. API HTTP

Gateway chạy cùng server, cùng origin với giao diện. Browser giữ bearer token riêng của gateway; việc đăng nhập được xác thực qua server TCP hiện có. Danh tính, quyền và phòng được lấy từ phiên, không tin `role`, `userId`, `roomId` hay `sessionId` tự khai.

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/api/health` | Trạng thái dịch vụ công khai |
| POST | `/api/auth/login` | Đăng nhập, có giới hạn tần suất |
| POST | `/api/auth/logout` | Đóng phiên web và kết nối lõi tương ứng |
| POST | `/api/auth/departing` | Báo đóng trang, có khoảng đệm trước khi dọn phiên |
| GET | `/api/me` | Danh tính, peer ID và phòng hiện tại |
| GET | `/api/rooms?page=1&pageSize=5&q=...` | Tìm/phân trang phòng chưa bị xóa |
| GET | `/api/rooms/current` | Code, phiên bản, thành viên, lịch sử và quyền của phiên hiện tại |
| GET | `/api/rooms/current/history` | Chi tiết lịch sử code trong phòng |
| POST | `/api/rooms/current/command` | Tạo/vào/rời/xóa phòng, chat và lệnh sửa code |
| GET | `/api/rooms/current/chat?after=N` | Tin gần nhất, ID mới nhất và số tin chưa đọc |
| GET | `/api/events?after=N` | Lấy sự kiện bằng polling và cursor |
| GET/POST | `/api/classroom` | Đọc trạng thái lớp hoặc thực hiện action |
| GET | `/api/classroom/branches/:userId?round=N` | Code nhánh và 20 lượt chấm gần nhất, theo quyền |
| GET | `/api/classroom/checks/:id` | Snapshot và kết quả bất biến của lượt chấm |
| GET | `/api/classroom/rounds/:N` | Nhánh/kết quả vòng cũ được phép đọc |
| POST | `/api/assignments` | Các request bài tập, luyện tập, chấm và cuộc thi |
| POST | `/api/call/signal` | Gửi tín hiệu cuộc gọi |
| GET | `/api/call/signals?after=N` | Danh sách peer và tín hiệu dành cho phiên |
| POST | `/api/screen/signal` | Gửi tín hiệu chia sẻ màn hình |
| GET | `/api/screen/signals?after=N` | Nhận tín hiệu chia sẻ màn hình |

Ngoài health và đăng nhập, các endpoint trên yêu cầu phiên xác thực; thao tác trong phòng cần membership và quyền tương ứng. Mã lỗi thường gặp: `400` dữ liệu sai, `401` chưa xác thực, `403` sai quyền, `409` xung đột trạng thái/phiên bản, `500` lỗi xử lý/lưu dữ liệu.

### 7.1. Lệnh phòng và chat

`POST /api/rooms/current/command` nhận `action` và dữ liệu tương ứng:

- `CREATE_ROOM`: gửi `roomCode`; chỉ giáo viên tạo, trả `roomId`/`roomCode` sau khi tạo và vào thành công. Thiếu mã trả 400, trùng mã trả 409.
- `JOIN_ROOM`: trường `roomId` nhận mã nhập; server ưu tiên tra mã công khai trước ID nội bộ để tương thích.
- `LEAVE_ROOM`: trả 200 cùng `leftRoomId` sau khi đã tách thành viên.
- `DELETE_ROOM`: nhận ID nội bộ bất biến, chỉ giáo viên chủ phòng được xóa. Lưu trạng thái xóa trước khi gỡ khỏi RAM; thành viên nhận `[ROOM_DELETED] ID`.

Danh sách phòng luôn 5 phòng/trang; `q` tối đa 160 ký tự. Kết quả có `rooms`, `page`, `pageSize`, `total`, `totalPages`, `customRoomCodes`, `emptyRoomAutoDelete`, `manualRoomDelete`. Mã công khai chỉ phục vụ người dùng; bài nộp và phân quyền vẫn dùng ID nội bộ.

Chat trả `messages`, `latestId`, `unreadCount`. Mỗi tin có `id`, `userId`, `name`, `text`, `timestamp`, `system=false`. `after=N` là mốc client đang giữ; GET không ghi biên nhận đọc lên server. Danh tính và phạm vi phòng không lấy từ query tự khai.

### 7.2. Action lớp học

Giáo viên chủ phòng dùng `START`, `SELECT_EXERCISE`, `MASTER_SAVE`, `FORK`, `FREEZE`, `RESUME`, `MERGE`, `END`, `NEW_SESSION`. Request gửi `expectedPhase` và `round`; chọn/sửa Master, Fork và Merge gửi thêm `expectedVersion` của Master. Chọn bài dùng `exerciseId`; lưu Master dùng `code`; Merge dùng `userId` và đúng `branchVersion` đang xem.

Sinh viên dùng `BRANCH_SAVE` với `round`, `expectedVersion`, `code`; hoặc `BRANCH_CHECK` với `round`, `expectedVersion` của bản đã lưu. Chỉ được làm khi phase là `BRANCHING`. Server luôn chọn nhánh của tài khoản đăng nhập. Source tối đa 32.000 byte UTF-8; kết quả chỉ thuộc bản hiện tại khi `latestCheck.branchVersion` khớp phiên bản nhánh.

### 7.3. Request bài tập và cuộc thi

`POST /api/assignments` nhận trường `type`. Các nhóm chính:

| Nhóm | Type |
|---|---|
| Ngân hàng đề/báo cáo | `CATALOG_LIST`, `CATALOG_GET`, `ROOM_STUDENTS_LIST`, `CLASS_PROGRESS` |
| Giao bài/starter | `ASSIGN_CREATE`, `ASSIGN_LIST`, `ASSIGN_GET`, `DEBUG_STARTER_PREPARE`, `DEBUG_STARTER_LOAD` |
| Chấm code chung | `SHARED_CODE_STATE`, `SHARED_CHECK_CREATE`, `SHARED_CHECK_LATEST`, `SHARED_CHECK_STATUS`, `SHARED_CHECK_RESULT` |
| Bài nộp cá nhân | `SUBMIT_CREATE`, `SUBMISSION_LIST`, `SUBMISSION_RESULT` |
| Cuộc thi | `CONTEST_CREATE`, `CONTEST_LIST`, `CONTEST_PROBLEMS_GET`, `CONTEST_SUBMIT`, `CONTEST_SUBMISSION_RESULT`, `CONTEST_SUBMISSION_LIST`, `LEADERBOARD_GET` |
| Chạy thử giáo viên | `TEACHER_TRIAL_CREATE`, `TEACHER_TRIAL_LIST`, `TEACHER_TRIAL_RESULT` |

Service kiểm tra quyền giáo viên/sinh viên, chủ phòng, người nhận bài và thời hạn cho từng type. Kết quả chạy thử giáo viên không tính vào điểm chính thức. Bài nộp đã nhận có thể được truy vấn/gửi lại theo cơ chế chống trùng dù tại thời điểm gửi lại đã hết hạn.

Luyện cá nhân không yêu cầu phòng, nhưng vẫn yêu cầu đăng nhập:

| Type | Dữ liệu chính | Kết quả |
|---|---|---|
| `PRACTICE_LIST` | `query`, `topic`, `difficulty`, `mode`, `filterStatus`, `page` | Danh sách, thống kê và phân trang 10 bài |
| `PRACTICE_GET` | `exerciseId` | Đề, starter/template, test công khai và tiến độ |
| `PRACTICE_SUBMIT` | `exerciseId`, `sourceCode`, `idempotencyKey` | Bài nộp có phiên bản theo tài khoản/bài |
| `PRACTICE_RUN` | Như SUBMIT | Chạy test công khai, `isRun=true`, `version=0` |
| `PRACTICE_SUBMISSIONS` | `exerciseId`, `page` | 10 phiên bản chính thức/trang, danh sách không chứa source |
| `PRACTICE_RESULT` | `submissionId` | Snapshot và kết quả của chính tài khoản |

Idempotency key ràng buộc tài khoản, bài, loại chạy/nộp và source. Test ẩn, đáp án chuẩn và lời giải tham khảo không được trả cho client; chẩn đoán từ test ẩn được làm sạch.

### 7.4. Tín hiệu cuộc gọi và chia sẻ màn hình

Cuộc gọi nhận `JOIN`, `STATE` với `audio`/`video`; `LEAVE`; hoặc `OFFER`, `ANSWER`, `ICE` với `toPeerId` và `payload` JSON dạng chuỗi. Hai bên phải chủ động tham gia và cùng phòng. Cursor tăng dần, client chỉ xác nhận mốc sau khi xử lý tín hiệu.

Giới hạn cuộc gọi: 600 gói/phút/người gửi; tối đa 512 gói hoặc 256.000 ký tự payload chờ/người nhận; ICE 4.096 ký tự, SDP 64.000 ký tự. Peer ngừng polling hơn 20 giây bị dọn khi registry được kiểm tra. Media và tín hiệu gọi không được lưu trong database.

Chia sẻ màn hình dùng tín hiệu `START`, `REQUEST`, `OFFER`, `ANSWER`, `ICE`, `END`, giới hạn theo phòng và người đang chia sẻ. Cả hai vai trò đều có thể phát/xem; server chỉ cho một người phát đồng thời. Gửi `START` lặp từ cùng phiên không tạo thêm sự kiện hoặc offer trùng.

<a id="tcp"></a>
## 8. Giao thức TCP

### 8.1. Kênh điều khiển 5000

Mỗi frame gồm **4 byte độ dài số nguyên có dấu big-endian**, theo sau là payload UTF-8, tối đa 65.536 byte. Payload là văn bản lệnh/phản hồi, không phải JSON. Phải đọc đủ frame, không dùng xuống dòng để xác định ranh giới TCP.

| Lệnh | Chức năng và quy tắc chính |
|---|---|
| `/login username password` | Đăng nhập; username tối đa 64 ký tự, password 256; đóng phiên sau 5 lần sai |
| `/create [mã phòng]` | Giáo viên tạo và vào phòng; console cũ có thể bỏ mã để sinh tự động |
| `/join mã phòng` | Vào phòng khi chưa tham gia phòng khác, hỗ trợ mã có khoảng trắng |
| `/members` | Lấy danh sách thành viên của phòng hiện tại |
| `/chat nội_dung` | Chat 1–2.000 ký tự Java trong phòng |
| `/code`, `/lines` | Xem code hoặc code có số dòng cùng trạng thái hiện tại |
| `/request` | Xin lượt sửa; xếp FIFO khi đã có người giữ lượt |
| `/release token` | Trả lượt khi đúng editor/token và không còn bản nháp |
| `/select version token 2,5,8` | Chọn các dòng rời hợp lệ, không trùng |
| `/select version token 4-7` | Chọn đoạn dòng liên tiếp |
| `/setline token line text` | Sửa một dòng đã chọn, nội dung không chứa xuống dòng |
| `/delete token line`, `/keep token line` | Xóa/giữ một dòng đã chọn |
| `/setrange token text` | Thay đoạn đã chọn, cho phép nhiều dòng |
| `/deleterange token`, `/keeprange token` | Xóa/giữ đoạn đã chọn |
| `/draft token` | Xem bản nháp do server ghép, chưa lưu |
| `/finish token` | Hoàn tất bản nháp khi đủ thao tác và đúng phiên bản/quyền |
| `/cancel token` | Hủy bản nháp và nhả lượt |
| `/update version token code` | Giáo viên chủ phòng thay toàn bộ code, cần đúng editor/token/version và không có bản nháp |
| `/history [version]` | Danh sách revision hoặc xem một phiên bản, không sửa code |
| `/kick sessionId` | Chủ phòng đóng kết nối thành viên, không tự kick mình |
| `/leave`, `/quit` | Rời phòng hoặc đóng client; server dọn quyền và hàng đợi |
| `/ping` | Giữ kết nối, server đáp `/pong` |
| `/screen-ticket publish`, `/screen-ticket view` | Xin vé một lần để chia sẻ/xem màn hình trong phòng |

Source phải khác rỗng, tối đa 32.000 byte UTF-8. Khi phiên học Master/nhánh đang áp dụng, lệnh sửa cũ vẫn chịu kiểm tra quyền tương ứng ở server. Giáo viên chủ phòng có đường xử lý ưu tiên lấy lại quyền sửa; bản nháp của người trước được giữ riêng để khôi phục qua gateway.

Client console có thêm `/load version token đường_dẫn` để đọc file UTF-8 rồi gửi cập nhật, hỗ trợ đường dẫn có khoảng trắng. `/heartbeat off` và `/heartbeat on` dùng để thử timeout, được xử lý phía client.

Các marker thường gặp: `[LOGIN_OK]`, `[LOGIN_FAILED]`, `[Phòng ...]`, `[Thành viên]`, `[Hệ thống]`, `[Bản nháp]`, `[COMMITTED]`, `[CANCELLED]`, `[NO_CHANGE]`, `[SAVE_FAILED]`, `[ROOM_DELETED]`. Parser phải giữ raw text khi chưa nhận diện được thông báo; giao thức văn bản không có request ID chung để ghép mọi phản hồi tuyệt đối.

Lịch sử dài được chia thành `[HISTORY_PART i/n]` theo byte UTF-8, không cắt giữa ký tự Unicode. Writer gửi hết các phần của một phản hồi rồi mới gửi sự kiện khác. Client ghép đủ các phần trước khi kết thúc hiển thị; thông báo local được hoãn để không chen vào lịch sử. Nếu mất kết nối giữa chừng, báo chưa nhận đủ.

### 8.2. Kênh màn hình 5001

Mỗi packet gồm 4 byte độ dài big-endian, body tối đa 2 MiB. Body bắt đầu bằng 1 byte loại packet rồi tới payload:

| Loại | ID | Payload |
|---|---|---|
| `HELLO` | 1 | Vé UTF-8 dùng một lần, phải gửi đầu tiên |
| `FRAME` | 2 | Dữ liệu JPEG |
| `STOP` | 3 | Rỗng |
| `ERROR` | 4 | Lý do lỗi UTF-8 |
| `STATUS` | 5 | Trạng thái/xác nhận UTF-8 |

Phản hồi cấp vé dạng `[SCREEN_TICKET] mode=PUBLISH room=... port=5001 ticket=...`, hoặc `mode=VIEW`. Phòng và cổng do server quyết định. Không gửi ảnh qua kênh điều khiển 64 KiB và không ghi đầy đủ vé vào log.

### 8.3. Kênh bài tập 5002

Mỗi request dùng kết nối TCP riêng, khung 4 byte độ dài big-endian và JSON UTF-8 tối đa 65.536 byte. Request có `protocolVersion: 1`, `requestId`, `type`, `sessionId` và các trường theo nghiệp vụ. Server lấy danh tính từ phiên đã đăng nhập và kiểm tra quyền phòng/bài. HTTP gateway gọi adapter trong cùng chương trình; không để browser tự cung cấp mã phiên lõi.

<a id="cham-bai"></a>
## 9. Chấm bài và bảo vệ dữ liệu

Code nộp được lưu thành snapshot trước khi đưa vào hàng đợi. Các trạng thái `QUEUED`, `RUNNING`, lỗi hệ thống và `SANDBOX_UNAVAILABLE` không phải AC. Các verdict bài làm gồm `ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED` và các giới hạn tài nguyên khác.

`DockerJavaSandbox` biên dịch/chạy Java trong container: tắt mạng, filesystem gốc chỉ đọc, giới hạn CPU/RAM/PID, thời gian và output. Test ẩn và đáp án không nằm trong thư mục code nộp truy cập được. Đầu vào được gửi qua stdin; đáp án giữ ở server. Không có Docker/image thì trả lỗi sandbox, không tự chạy code sinh viên trực tiếp trong JVM server.

Module bài tập/luyện tập/cuộc thi dùng chung queue, mặc định 32 mục và 2 worker. Worker phòng học riêng có 1 luồng, hàng đợi 32 lượt; mỗi sinh viên tối đa 1 lượt đang chờ/chấm, cách nhau ít nhất 3 giây. Giao diện tự kiểm tra cách nhau tối thiểu 5 giây; tối đa 128 nhánh/vòng.

Các JVM properties cấu hình chính:

| Property | Mặc định |
|---|---|
| `studyfix.port` | `5000` |
| `studyfix.screen.port` | `5001` |
| `studyfix.assignment.port` | `5002` |
| `studyfix.db` | `data/studyfix.db` tính từ thư mục chạy server |
| `studyfix.learning.db` | `data/studyfix-learning.db` |
| `studyfix.assignment.queue` | `32` |
| `studyfix.assignment.workers` | `2` |
| `studyfix.assignment.docker.image` | `eclipse-temurin:21-jdk` |

Các launcher hiện đặt sẵn một số cổng và đường dẫn; nếu thay cấu hình, cần giữ chúng thống nhất với client. Web dùng biến môi trường `STUDYFIX_WEB_PORT`, `STUDYFIX_WEB_KEYSTORE`, `STUDYFIX_WEB_KEYSTORE_PASSWORD` như phần chạy ứng dụng.

Mật khẩu được lưu bằng PBKDF2-HMAC-SHA256, 600.000 vòng, salt ngẫu nhiên 16 byte và output 256 bit. `userId` là danh tính tài khoản; `sessionId` là kết nối đăng nhập. Client hiển thị dữ liệu nhưng không tự quyết định quyền, điểm hoặc kết quả đạt.

<a id="du-lieu"></a>
## 10. Database, sao lưu và khôi phục

### 10.1. Hai database khác nhau

| File | Nội dung |
|---|---|
| `server/data/studyfix.db` | Tài khoản, phòng, revision, phiên học, nhánh, kết quả chấm phòng học, sự kiện và chat |
| `server/data/studyfix-learning.db` | Ngân hàng đề, bài giao, bài nộp, luyện tập, cuộc thi và chạy thử giáo viên |

Database runtime được tạo khi server chạy nếu chưa có; thư mục làm việc quyết định đường dẫn tương đối. Không dùng việc xóa database để xử lý lỗi đăng nhập/chấm bài. Một tiến trình server sở hữu bộ database đang chạy.

Phòng giữ `public_code`, `code_key`, `active`, tách mã hiển thị khỏi khóa chính số. Xóa phòng chỉ đánh dấu không hoạt động và giữ dữ liệu chi tiết. Phòng đã inactive từ phiên bản cũ không tự được mở lại.

Core migration thêm các cột lịch sử còn thiếu và điền dữ liệu từ snapshot có sẵn, không đoán tác giả hay tạo phiên bản bị thiếu. Dữ liệu lớp học nằm trong `classroom_sessions`, `classroom_branches`, `classroom_checks`, `classroom_events`, `classroom_chat`; `classroom_schema` theo dõi phiên bản cấu trúc. Merge ghi Master/revision, phase và sự kiện trong cùng giao dịch. Commit thành công mới đổi RAM và phát sự kiện; lỗi thì rollback.

Learning database dùng `p3_schema_meta` để nâng cấp tăng dần; các thay đổi gồm liên kết cuộc thi trực tiếp ngân hàng đề, kết quả test riêng, chạy thử giáo viên, snapshot người nhận và `p3_practice_submissions` cho luyện tập. Hai database không dùng chung một schema. Lượt chấm bị gián đoạn được xử lý khi khởi động lại; phòng học đánh dấu lượt dang dở là `JUDGE_ERROR` để người dùng chạy lại. Chat cũ từng chỉ có trong RAM không thể tự khôi phục.

### 10.2. Sao lưu trước khi nâng cấp

1. Dừng Server Manager và các tiến trình server, chờ đóng kết nối database.
2. Sao chép toàn bộ `server/data/` sang vị trí backup riêng có thời điểm rõ ràng. Giữ cả `.db`, `-wal` và `-shm` nếu tồn tại.
3. Giữ phiên bản mã nguồn/binary tương ứng với backup.
4. Thử bản cập nhật trên bản sao database trước; kiểm tra đăng nhập, phòng, code/lịch sử, chat, bài giao và cuộc thi cũ.
5. Chỉ dùng bản mới với dữ liệu chính sau khi kiểm tra đạt.

Ví dụ sao lưu từ `StudyFix`, sau khi đã dừng server:

```powershell
$backupPath = Join-Path '..' ('StudyFix_data_backup_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
Copy-Item -LiteralPath '.\server\data' -Destination $backupPath -Recurse
```

### 10.3. Khôi phục bản cũ

Dừng toàn bộ server. Giữ riêng bộ database sau nâng cấp để có thể đối chiếu, khôi phục nguyên bộ backup cùng mã nguồn/binary cũ tương ứng, rồi kiểm tra lại các chức năng chính. Không mở database đã nâng cấp bằng binary cũ khi chưa xác nhận tương thích và không tự sửa foreign key/xóa bảng để ép chạy.

Nếu migration lỗi, giữ log và bản sao dữ liệu để tìm nguyên nhân trước khi chạy tiếp.

<a id="kiem-thu"></a>
## 11. Kiểm thử

### 11.1. Server Java

Từ thư mục `StudyFix/server`, với JDK 26 và Ant:

```powershell
.\test-all.ps1
```

Hoặc chọn target phù hợp:

| Lệnh | Phạm vi |
|---|---|
| `ant integration-test` | Hồi quy TCP, SQLite, lịch sử, recovery, gateway, phòng học và các luồng tích hợp |
| `ant edit-session-test` | Chọn dòng/đoạn, bản nháp và thao tác client console |
| `ant classroom-test` | Phân quyền nhánh, phiên bản, snapshot, Merge và phục hồi |
| `ant meeting-test` | HTTP/signaling cuộc gọi |
| `ant chat-test` | Quyền đọc chat, danh tính người gửi và số tin chưa đọc |
| `ant room-test` | Mã phòng, tìm kiếm, phân trang, xóa và giữ phòng sau restart |
| `ant practice-test` | Luyện tập, snapshot, chống trùng và riêng tư |
| `ant practice-docker-test` | Chạy/chấm Java thật trong Docker |
| `.\test-assignment-module.ps1` | Kiểm thử riêng module bài tập |

Các test dùng database tạm dưới `server/build/`, không dùng database học tập thật. Một số test Swing cần desktop; trường hợp headless có thể bỏ qua phần giao diện theo thiết kế suite. Test Docker cần Engine và image sẵn sàng. Dừng server demo trước khi chạy toàn bộ suite để tránh xung đột cổng/file.

### 11.2. Client Swing

Từ thư mục `StudyFix/client`:

```powershell
.\self-test.ps1
.\member3-self-test.ps1
```

### 11.3. Frontend

Từ thư mục `StudyFix/web-client`:

```powershell
Get-ChildItem .\static -Recurse -Filter *.js | ForEach-Object { node --check $_.FullName }
node .\tests\check_imports.mjs
```

Chạy mọi suite frontend và báo lỗi nếu có suite không đạt:

```powershell
$failedSuites = @()
Get-ChildItem .\tests -Filter 'check_*.mjs' | ForEach-Object {
    node $_.FullName
    if ($LASTEXITCODE -ne 0) { $failedSuites += $_.Name }
}
if ($failedSuites.Count) { throw ('Test lỗi: ' + ($failedSuites -join ', ')) }
```

| File kiểm thử | Phạm vi |
|---|---|
| `check_imports.mjs` | Import/export và vòng phụ thuộc module |
| `check_seven_bugfixes.mjs` | Các điều kiện hồi quy cho bảy nhóm lỗi đã sửa |
| `check_classroom.mjs` | Workspace, vai trò, nháp và phản hồi muộn |
| `check_meeting.mjs` | Offer/answer, transceiver, ICE, mic/camera và dọn media |
| `check_meeting_navigation.mjs` | Rời cuộc họp, rời phòng, chuyển trang và tải lại |
| `check_screen_audio.mjs` | Yêu cầu lấy/gửi/phát âm thanh chia sẻ màn hình |
| `check_chat_notifications.mjs` | Tin chưa đọc, focus, cuộn chat và mốc lưu trên máy |
| `check_room_directory.mjs` | Tìm kiếm, phân trang, quyền xóa và phản hồi cũ |
| `check_practice.mjs` | Luyện tập, import file, lịch sử, nháp và chống trùng |

Các suite mô phỏng DOM/HTTP/WebRTC không thay thế kiểm tra trên trình duyệt thật. Kết quả AC giả lập trong test lưu trữ cũng không chứng minh bộ chấm Docker thật đã chạy.

### 11.4. Nghiệm thu thủ công

Đăng nhập giáo viên và ít nhất hai sinh viên. Tạo/vào cùng phòng, thử Master → Fork → lưu/chấm nhánh → Freeze → Merge và xem lại vòng cũ. Kiểm tra sinh viên không xem được nhánh người khác; giáo viên khác không có quyền chủ phòng.

Thử luyện tập khi chưa vào phòng: chạy ví dụ, nộp đúng/sai, xem đúng snapshot lịch sử, đổi bài và tải lại để kiểm tra nháp. Thử bài giao/cuộc thi và giới hạn thời hạn.

Trên ít nhất hai máy LAN, thử mic/camera cả hai chiều với thứ tự tham gia khác nhau, chia sẻ tab có âm thanh, từ chối quyền, rời phòng và mất mạng. Kiểm tra tin chưa đọc khi tab ẩn hoặc đang đọc chat cũ. Dừng/chạy lại server để xác nhận phòng, code và lịch sử còn nguyên. Đây là các bước nghiệm thu cần thực hiện trên môi trường sử dụng, không phải tuyên bố đã kiểm chứng media/tải LAN.

<a id="xu-ly-loi"></a>
## 12. Lỗi thường gặp và giới hạn

| Hiện tượng | Cách xử lý |
|---|---|
| Không tìm thấy `java`, `javac` hoặc Ant | Kiểm tra JDK đầy đủ, `JAVA_HOME`, `ANT_HOME` và `PATH` trong terminal đang dùng |
| PowerShell chặn script | Dùng `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` trong phiên hiện tại |
| Cổng bận hoặc không xóa được `dist/studyfix.jar` | Dừng server cũ, chờ giải phóng JAR/cổng rồi chạy lại launcher |
| Build client lỗi file đang sử dụng | Đóng mọi cửa sổ client trước khi build; mở thêm client bằng `run-client-only.ps1` |
| `SANDBOX_UNAVAILABLE` | Kiểm tra Docker Engine và image `eclipse-temurin:21-jdk`; lỗi này không phải AC hay bài làm sai |
| Không bật được mic/camera/chia sẻ qua IP LAN | Kiểm tra HTTPS, chứng chỉ tin cậy và quyền thiết bị của trình duyệt |
| Chia sẻ có hình nhưng không có tiếng | Chọn lại tab trình duyệt và bật chia sẻ âm thanh tab; kiểm tra nút phát ở máy nhận |
| Không thấy giao diện mới sau sửa | Restart server nếu đổi Java; tải lại cứng bằng Ctrl+Shift+R để lấy JS/CSS mới |
| Xung đột phiên bản khi lưu | Lấy lại trạng thái mới, giữ/tải nháp trước khi quyết định thay nội dung |
| Restart xong không còn tham gia phòng | Đăng nhập và vào lại mã phòng; kết nối và quyền sửa không phục hồi từ phiên cũ |

Chưa có quản lý lớp/khóa học độc lập, thi đội đầy đủ, chống gian lận hoặc tự đăng nhập lại sau server restart. Mốc đọc chat chỉ lưu theo trình duyệt. Lịch sử code chưa phân trang ở database. Media chưa có SFU/TURN, kiểm chứng tải lớp đông hoặc bảo đảm hoạt động qua Internet. Hệ thống không ghi lại media vào database.

<a id="thu-vien"></a>
## 13. Thư viện đi kèm

SQLite JDBC nằm tại `server/lib/sqlite-jdbc-3.53.0.0.jar`, tọa độ dependency `org.xerial:sqlite-jdbc:3.53.0.0`. Ant tham chiếu qua `file.reference.sqlite-jdbc.jar` trong `server/nbproject/project.properties` và có bước chép dependency khi đóng gói.

- Dự án Xerial: <https://github.com/xerial/sqlite-jdbc>.
- Artifact: <https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.0.0/sqlite-jdbc-3.53.0.0.jar>.
- SHA-256 theo thông tin thư viện đi kèm: `303e8150100982f2ed7d1b82d897278ef7744bd494c28ad4e7042b7914591697`.
- Driver có notice/license trong JAR; giữ nguyên khi phân phối. Xerial sử dụng Apache-2.0/BSD-2-Clause; SQLite thuộc public domain.

Đối chiếu checksum từ thư mục `StudyFix`:

```powershell
Get-FileHash -LiteralPath '.\server\lib\sqlite-jdbc-3.53.0.0.jar' -Algorithm SHA256
```

Nhập OJ ZIP hiện hỗ trợ số bài/Week linh hoạt, nhập bổ sung và giữ dữ liệu cũ. Xem [hợp đồng gói và giới hạn an toàn](docs/NHAP_GOI_OJ_ZIP_VI.md).
