# Hướng dẫn hiểu logic và đọc source StudyFix

> **Cập nhật phạm vi:** thư mục client Java Swing đã được xóa theo yêu cầu. Các phần Swing trong tài liệu này là mô tả source trước khi xóa. Để đọc phân tích hiện tại tập trung vào web/server, xem [Phân tích chi tiết dự án web và server](PHAN_TICH_CHI_TIET_DU_AN_WEB_SERVER_VI.md), gồm giao thức, lớp học online, Master/nhánh/Merge, luyện tập, chấm Docker và bản đồ file/hàm.

**Đối chiếu với source trong workspace ngày 04/11/2026.**

Tài liệu này giải thích dự án theo cách: **chức năng ở đâu → hàm nào xử lý → dữ liệu đi qua đâu → vì sao viết như vậy → ví dụ để tự lần theo**. Các ví dụ về người dùng, phiên bản và kết quả dưới đây dùng để giải thích logic, không phải số liệu thực nghiệm hoặc thông tin thành viên nhóm.

Bạn nên mở file này bằng **Open Preview** trong VS Code và mở source ở cửa sổ bên cạnh. Nhấn vào đường dẫn để đến file, sau đó **Ctrl+F tên hàm**. Phụ lục cuối tài liệu có thêm số dòng của những điểm đọc quan trọng tại thời điểm lập hướng dẫn; số dòng có thể thay đổi khi sửa source.

## Mục lục

1. [Hiểu toàn hệ thống trước khi đọc từng dòng](#1-hiểu-toàn-hệ-thống-trước-khi-đọc-từng-dòng)
2. [Bản đồ các thư mục](#2-bản-đồ-các-thư-mục)
3. [Các khái niệm dễ nhầm](#3-các-khái-niệm-dễ-nhầm)
4. [Luồng mở server và mở client](#4-luồng-mở-server-và-mở-client)
5. [Giải thích TcpControlConnection](#5-giải-thích-tcpcontrolconnection)
6. [Giải thích AssignmentConnection](#6-giải-thích-assignmentconnection)
7. [Giải thích ClientModule và cách ghép module](#7-giải-thích-clientmodule-và-cách-ghép-module)
8. [Server nhận lệnh và xác thực như thế nào](#8-server-nhận-lệnh-và-xác-thực-như-thế-nào)
9. [Logic phòng học và rời cuộc họp](#9-logic-phòng-học-và-rời-cuộc-họp)
10. [Logic sửa code và kiểm soát phiên bản](#10-logic-sửa-code-và-kiểm-soát-phiên-bản)
11. [Logic Master và nhánh riêng](#11-logic-master-và-nhánh-riêng)
12. [Logic luyện code và chấm bài](#12-logic-luyện-code-và-chấm-bài)
13. [Giao bài và kỳ thi](#13-giao-bài-và-kỳ-thi)
14. [Chat và thông báo chưa đọc](#14-chat-và-thông-báo-chưa-đọc)
15. [Mic camera và chia sẻ màn hình](#15-mic-camera-và-chia-sẻ-màn-hình)
16. [Database lưu những gì](#16-database-lưu-những-gì)
17. [Các luồng chạy song song](#17-các-luồng-chạy-song-song)
18. [Bản đồ file phía web](#18-bản-đồ-file-phía-web)
19. [Bản đồ file client Swing](#19-bản-đồ-file-client-swing)
20. [Bản đồ file phía server](#20-bản-đồ-file-phía-server)
21. [Cách lần theo và debug một chức năng](#21-cách-lần-theo-và-debug-một-chức-năng)
22. [Thứ tự học và bài tự kiểm tra](#22-thứ-tự-học-và-bài-tự-kiểm-tra)
23. [Những giới hạn cần hiểu đúng từ source](#23-những-giới-hạn-cần-hiểu-đúng-từ-source)
24. [Tra nhanh các vị trí code quan trọng](#24-tra-nhanh-các-vị-trí-code-quan-trọng)

## 1. Hiểu toàn hệ thống trước khi đọc từng dòng

Trong phạm vi bạn đang sử dụng, StudyFix gồm **client web chạy trên trình duyệt** và **backend Java**. Sơ đồ dưới đây chỉ mô tả luồng web. Client Java Swing vẫn còn trong source nhưng không cần chạy để sử dụng web.

```text
CLIENT WEB — trình duyệt của giáo viên / học sinh
web-client/static/index.html + app.js + các module JavaScript
        |
        | HTTP/HTTPS — cổng mặc định 8080
        | tải giao diện, gửi API, polling lấy cập nhật
        v
BACKEND JAVA — WebGatewayServer
        |
        +-- Đăng nhập, lệnh phòng, chat, code cộng tác
        |       |
        |       | TCP nội bộ tới 127.0.0.1:5000
        |       v
        |   Server -> ClientHandler -> Room / Store
        |                                  |
        |                                  v
        |                             studyfix.db
        |
        +-- Phiên giảng dạy: Master / nhánh / Merge
        |       |
        |       | gọi hàm Java trong cùng JVM
        |       v
        |   ClassroomService + StudyFixGateway
        |       -> trạng thái phòng / lưu phiên và phiên bản
        |       -> kiểm tra code: queue riêng -> DockerJavaSandbox
        |
        +-- Luyện code, bài tập, kỳ thi
        |       |
        |       | gọi hàm Java trong cùng JVM
        |       v
        |   AssignmentTcpServer.handleWebRequest()
        |       -> AssignmentService -> Repository
        |               |                  |
        |               |                  v
        |               |          studyfix-learning.db
        |               | khi chạy thử / nộp code
        |               v
        |          Queue -> Worker -> DockerJavaSandbox
        |                                  |
        |                                  v
        |                       Lưu kết quả vào database
        |                       Web polling API để hiển thị
        |
        +-- Cuộc họp: chuyển tiếp signaling WebRTC qua HTTP
                |
                v
        Các trình duyệt trao đổi mic / camera / màn hình
        qua kết nối WebRTC giữa các peer
```

**Cách đọc sơ đồ:**

- Trình duyệt gọi cổng web **8080**; không kết nối trực tiếp tới các cổng TCP native 5000, 5001, 5002.
- **TCP 5000 vẫn được backend sử dụng nội bộ** để gateway đăng nhập và chuyển lệnh tới server lõi. Bỏ client Swing không có nghĩa là bỏ server TCP lõi.
- Web dùng lại hàm `AssignmentTcpServer.handleWebRequest()` bằng cách gọi Java trong cùng tiến trình; luồng này **không truyền yêu cầu qua TCP 5002**.
- Phần Master/nhánh dùng `ClassroomService`, có hàng đợi kiểm tra code riêng; không gộp mọi nghiệp vụ giảng dạy vào `AssignmentService`.
- Gateway trao đổi tín hiệu thiết lập cuộc gọi; âm thanh và hình ảnh web đi qua WebRTC, không đi qua relay JPEG TCP 5001 của Swing.
- Các chương 5–7 và 19 mô tả client Swing còn trong source để tra cứu toàn dự án. Nếu chỉ học luồng web, có thể bỏ qua các chương đó và ưu tiên chương 8–18, 20–21.

**Ví dụ dễ hình dung:** giao diện là bàn tiếp nhận; service là nơi quyết định yêu cầu có được làm hay không; repository là nơi ghi sổ; queue là rổ bài chờ chấm; worker lấy bài ra; Docker là môi trường riêng để chạy code người học.

Ba câu quan trọng:

- Trang Tổng quan giáo viên vẫn là **client**, dù mở trên chính máy chạy server.
- Server Manager là **giao diện quản lý và khởi động các dịch vụ**, không phải trang Tổng quan giáo viên.
- Web gọi HTTP vào Java; dự án không có backend Node.js chỉ vì giao diện sử dụng JavaScript.

## 2. Bản đồ các thư mục

```text
StudyFix/
├── server/
│   ├── src/studyfix/                  Backend lõi, HTTP gateway, phòng, phiên học
│   │   ├── assignment/               Giao bài, luyện tập, thi, queue và chấm code
│   │   └── screenshare/              Relay ảnh màn hình cho client native
│   ├── test/                         Kiểm thử Java
│   ├── data/                         Database khi chạy bằng launcher mặc định
│   ├── lib/                          SQLite JDBC
│   ├── build.xml                     Cách build và chạy các suite test Ant
│   ├── run-server-manager.ps1         Mở Server Manager
│   └── run-with-assignments.ps1        Mở server chế độ console
├── client/
│   ├── src/main/java/vn/studyfix/client/
│   │   ├── net/                      Kết nối TCP và framing
│   │   ├── protocol/                 Đóng lệnh và phân tích phản hồi
│   │   ├── service/                  Hàm nghiệp vụ phía client
│   │   ├── controller/               Điều phối dữ liệu cho giao diện Swing
│   │   ├── ui/                       Các màn hình/panel Swing
│   │   ├── integration/              Hợp đồng ghép các module
│   │   ├── exercise/                 Giao diện bài tập và kết nối 5002
│   │   └── screen/                   Chụp và truyền ảnh màn hình native
│   └── src/main/resources/           Cấu hình client
├── web-client/
│   ├── static/                       Giao diện web đang được phát triển
│   │   ├── core/                     State, API, polling, sự kiện và điều hướng
│   │   ├── features/                 Auth, phòng, bài tập, cuộc họp, thi
│   │   ├── ui/                       Render, component, modal, toast
│   │   └── shared/                   Format dữ liệu và crypto helper
│   └── tests/                        Kiểm thử frontend
└── docs/                             Hướng dẫn và bản phác giao diện
```

`build/`, `build-launcher/`, `dist/`, `target/` là nơi sinh ra kết quả build/test. Muốn tìm logic, ưu tiên `src/` và `web-client/static/`. Muốn tìm dữ liệu đã lưu, xem cấu hình database; không lấy thư mục build làm nguồn dữ liệu chính.

**Đừng nhầm:** nếu bạn còn giữ `CODE_PRACTICE_PREVIEW.html` từ giai đoạn thiết kế thì đó là bản phác giao diện. Trang Luyện code thật nằm ở [practice.js](../web-client/static/features/exercises/practice.js), gọi API và lưu bài vào backend.

## 3. Các khái niệm dễ nhầm

| Tên | Ý nghĩa trong dự án | Ví dụ minh họa |
|---|---|---|
| `userId` | Danh tính tài khoản, ổn định qua nhiều lần đăng nhập | Một sinh viên đăng nhập hai lần vẫn cùng userId |
| `sessionId` / `memberId` | Phiên kết nối TCP đã đăng nhập | Cùng tài khoản mở hai kết nối có thể có hai sessionId |
| Web bearer `token` | Mã truy cập phiên web, gửi qua header Authorization | `Bearer <mã phiên>`; không nên gọi nó là JWT nếu chưa có cơ chế JWT |
| `peerId` | Định danh thiết bị/phiên dùng cho signaling WebRTC | Tín hiệu OFFER gửi từ peer này tới peer khác |
| `roomId` | ID nội bộ của phòng, dùng cho quan hệ dữ liệu | ID nội bộ `12` |
| `roomCode` | Mã hiển thị do giáo viên nhập | `Lớp Java A` |
| Edit `token` | Số dùng xác nhận lượt giữ quyền sửa code chung | Token lượt cũ là 8, lượt mới là 9 |
| Master `version` | Phiên bản code chung đã commit | Master v4 |
| Branch `version` | Phiên bản riêng của nhánh một sinh viên trong một vòng | Nhánh của An v2 |
| Practice `version` | Số lần nộp chính thức của một tài khoản cho một bài | Bài MATH001 của An có v1, v2, v3 |
| `submissionId` | ID bất biến của một lần nộp | Dùng để hỏi lại đúng kết quả và source của lần đó |
| `idempotencyKey` | Khóa nhận biết một yêu cầu nộp đã được tiếp nhận | Retry sau mất mạng dùng lại khóa cũ |

**Ví dụ:** An sửa editor sau khi nộp v1. Nội dung mới là bản nháp, chưa phải v2. Chỉ khi gửi một lần nộp chính thức mới thì hệ thống tạo phiên bản tiếp theo. Kết quả v1 luôn gắn với source đã lưu của v1.

## 4. Luồng mở server và mở client

### 4.1. Server Manager

Đọc theo thứ tự:

1. [run-server-manager.ps1](../server/run-server-manager.ps1): xác định công cụ, kiểm tra server cũ và build JAR.
2. [Studyfix.java](../server/src/studyfix/Studyfix.java): `main()` gọi `ServerAdmin.main(args)`.
3. [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java): dựng Swing UI; `startServices()` chạy ở background.
4. [AssignmentModuleConfig.java](../server/src/studyfix/assignment/AssignmentModuleConfig.java): lấy cổng, database, số worker, kích thước queue, image Docker.
5. `startServices()` tạo `Server`, `AssignmentTcpServer`, `WebGatewayServer`, rồi gọi `start()`.

| Dịch vụ | Cổng mặc định | Vai trò |
|---|---:|---|
| `Server` | 5000 | Điều khiển TCP, đăng nhập, chat và thành viên phòng |
| `ScreenShareRelay` | 5001 | Ảnh màn hình cho client native |
| `AssignmentTcpServer` | 5002 | JSON TCP cho module bài tập native |
| `WebGatewayServer` | 8080 | Static web, API HTTP/HTTPS và signaling |

`new Server(...)` đã chuẩn bị socket/database/relay; `core.start()` mới khởi chạy vòng lặp accept của kênh điều khiển. Khi có lỗi mở một dịch vụ, code khởi động có nhánh đóng những tài nguyên đã tạo, tránh để lại một nửa hệ thống đang chạy.

Nếu chạy console, đọc [AssignmentEnabledServerMain.java](../server/src/studyfix/assignment/AssignmentEnabledServerMain.java). Nó tạo các dịch vụ tương tự, đăng ký shutdown hook và dùng `CountDownLatch` để giữ chương trình hoạt động.

### 4.2. Client Swing

[StudyFixApplication.java](../client/src/main/java/vn/studyfix/client/StudyFixApplication.java) thực hiện:

```text
Đọc ClientConfig
    → mở LoginFrame trên Swing EDT
    → AuthService.login()
    → TcpControlConnection.connect()
    → gửi /login và chờ LOGIN_OK
    → tạo SessionInfo
    → mở MainFrame
```

[ClientConfig.java](../client/src/main/java/vn/studyfix/client/config/ClientConfig.java) đọc resource `studyfix-client.properties`; các system property như `studyfix.host`, `studyfix.port` có thể ghi đè cấu hình mặc định.

### 4.3. Client web

[app.js](../web-client/static/app.js) đăng ký trang và gọi `init()` trong [lifecycle.js](../web-client/static/core/lifecycle.js).

- Chưa có token: render trang đăng nhập.
- Có token: hỏi `/api/me`, tải phòng hiện tại nếu có, khôi phục trang rồi bắt đầu polling.
- Trang Luyện code có nhánh riêng trong `loadPageData()`, nên vẫn tải được khi `state.room` chưa có.

**Ví dụ:** tải lại trình duyệt không thể chỉ dựa vào một biến JavaScript cũ trong RAM. `init()` phải hỏi server để xác nhận phiên trước khi dùng state đã nhớ trong trình duyệt.

## 5. Giải thích TcpControlConnection

File: [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java).

Đây là phần giữ **một kết nối TCP điều khiển lâu dài**. Nó phụ trách gửi/nhận dữ liệu; không tự quyết định ai được sửa bài hoặc ai là chủ phòng.

### 5.1. Các trường quan trọng

| Trường | Để làm gì | Cách hiểu đơn giản |
|---|---|---|
| `socket` | Kết nối với server | Đường dây đang mở |
| `in`, `out` | Đọc/ghi byte từ socket | Hai đầu nhận và gửi |
| `outbound` | Queue lệnh chờ gửi, sức chứa 256 | Lệnh được xếp hàng trước khi ghi |
| `listeners` | Những nơi muốn nghe message | Một message có thể cập nhật nhiều panel |
| `waiters` | Những yêu cầu đang chờ một loại phản hồi | Ví dụ đăng nhập chờ LOGIN_OK/LOGIN_FAILED |
| `assembler` | Ghép nhiều phần lịch sử | Chỉ phát lịch sử khi đã nhận đủ phần |
| `heartbeat` | Định kỳ gửi `/ping` | Báo với server rằng kết nối còn hoạt động |
| `closed` | Cờ đóng kết nối | Đảm bảo xử lý đóng chỉ diễn ra một lần |

### 5.2. `connect()` mở kết nối

Code tạo `Socket`, gọi `connect(new InetSocketAddress(host, port), timeoutMillis)` và bật keepalive. Nếu khởi tạo lớp thất bại, socket được đóng trong nhánh `catch`.

**Ví dụ:** nhập IP máy giáo viên thay vì `127.0.0.1`. `connect()` sẽ mở kết nối tới máy đó; nghiệp vụ đăng nhập chưa xảy ra cho đến khi `AuthService` gửi lệnh.

### 5.3. Vì sao có reader và writer riêng

Constructor tạo hai luồng:

```java
Thread reader = daemon(this::readLoop, "studyfix-tcp-reader");
Thread writer = daemon(this::writeLoop, "studyfix-tcp-writer");
reader.start();
writer.start();
```

- `readLoop()` chờ server gửi message.
- `writeLoop()` chờ queue có lệnh rồi ghi xuống socket.
- UI không cần đứng chờ từng lần socket đọc xong mới phản hồi được.

**Ví dụ:** trong lúc An chưa bấm nút nào, Bình gửi chat. Reader vẫn nhận chat. Cùng lúc đó An có thể bấm xin quyền sửa; lệnh được đưa vào queue của writer.

### 5.4. `send()` chưa phải là gửi xong

Điểm mấu chốt của `send()` là:

```java
if (!outbound.offer(message))
    throw new IOException("Hàng đợi gửi đầy; cần kết nối lại để đồng bộ trạng thái");
```

`offer()` đưa message vào queue mà không chờ vô hạn. Writer mới là nơi thực sự gọi `FrameCodec.write(out, message)`.

Vì vậy:

```text
send() thành công
    = lệnh đã vào queue gửi
    ≠ server đã nhận và xử lý thành công
```

Ví dụ queue đầy sẽ báo lỗi. Code không âm thầm bỏ một lệnh sửa bài rồi giả vờ đã lưu thành công.

### 5.5. `sendAndAwait()` hoạt động thế nào

Hàm này thêm một `Waiter`, gửi lệnh, rồi chờ `CompletableFuture` hoàn thành trong thời gian cho phép.

```text
AuthService gọi sendAndAwait(/login ...)
    → đăng ký matcher LOGIN_OK hoặc LOGIN_FAILED
    → send() đưa lệnh vào queue
    → writer ghi lệnh
    → reader nhận một message phù hợp matcher
    → waiter.future.complete(message)
    → AuthService nhận được kết quả
```

`Predicate<String>` nghĩa là một hàm nhận chuỗi và trả về đúng/sai. Ví dụ:

```java
s -> s.startsWith("[LOGIN_OK]") || s.startsWith("[LOGIN_FAILED]")
```

Đây là cách nhận biết **loại phản hồi đang chờ**, không phải cơ chế request ID tổng quát cho mọi lệnh TCP. Không nên tự thêm nhiều yêu cầu giống nhau chạy đồng thời rồi cho rằng matcher luôn phân biệt được chúng.

### 5.6. `readLoop()` chia dữ liệu cho những ai

Thứ tự trong source:

1. `FrameCodec.read(in)` đọc một frame.
2. `assembler.accept(frame)` thử ghép lịch sử dài.
3. Nếu chưa đủ phần thì tiếp tục chờ.
4. Message hoàn chỉnh được đưa cho các waiter phù hợp.
5. Message cũng được đưa cho các listener.

Trong [MainFrame.java](../client/src/main/java/vn/studyfix/client/ui/MainFrame.java), listener đưa chuỗi vào `ServerMessageParser`, sau đó dùng `SwingUtilities.invokeLater(...)` để gọi `RoomController.accept(...)` trên luồng giao diện.

### 5.7. Heartbeat và đóng kết nối

Client Swing này định kỳ gửi `/ping` mỗi 5 giây. `fail()` dùng `closed.compareAndSet(false, true)` để một lỗi không kích hoạt đóng lặp nhiều lần; sau đó dừng heartbeat, đóng socket, đánh thức các waiter bằng exception và báo cho listener.

**Điểm cần hiểu đúng:** trong chính `TcpControlConnection`, heartbeat là gửi ping định kỳ; không thấy bộ đếm deadline PONG riêng như một số đoạn ở client console. Việc gửi ping, TCP keepalive và timeout của `sendAndAwait()` là ba cơ chế khác nhau.

`close()` có thử xếp `/quit` vào queue rồi gọi `fail()` đóng kết nối. Do writer chạy bất đồng bộ, không nên diễn giải rằng mọi lần gọi `close()` đều chắc chắn đã truyền xong `/quit` trước khi socket đóng.

## 6. Giải thích AssignmentConnection

File: [AssignmentConnection.java](../client/src/main/java/vn/studyfix/client/exercise/AssignmentConnection.java).

Lớp này gửi JSON tới **cổng 5002**, tách khỏi `TcpControlConnection` ở cổng 5000.

### 6.1. Những thông tin constructor giữ lại

```java
private final String host;
private final int port;
private final String sessionId;
```

- `host`: địa chỉ máy server.
- `port`: cổng module bài tập.
- `sessionId`: phiên đã đăng nhập qua kết nối điều khiển.

**Ví dụ:** người dùng đã đăng nhập qua 5000. Khi mở danh sách bài, module dùng cùng danh tính phiên đó để gọi 5002; không bắt nhập lại tài khoản cho từng yêu cầu bài tập.

### 6.2. `request()` dựng JSON

Đoạn đầu của hàm lần lượt thêm:

```java
req.put("protocolVersion", 1);
req.put("requestId", UUID.randomUUID().toString());
req.put("type", type);
req.put("sessionId", sessionId);
req.put("roomId", roomId == null ? "" : roomId);
req.putAll(fields);
```

| Trường | Ý nghĩa |
|---|---|
| `protocolVersion` | Nhãn phiên bản giao thức phía client gửi |
| `requestId` | Mã nhận biết yêu cầu để phía trả lời gắn lại |
| `type` | Nghiệp vụ: ví dụ `ASSIGN_LIST`, `SUBMIT_CREATE` |
| `sessionId` | Phiên TCP đã xác thực |
| `roomId` | Ngữ cảnh phòng đối với nghiệp vụ cần phòng |
| Các field khác | Ví dụ `assignmentId`, `sourceCode`, `exerciseId` |

Lưu ý khi mở rộng: `putAll(fields)` chạy sau các field chuẩn, nên caller không nên truyền key trùng `sessionId`, `type`, `requestId`. Lớp client này không phải nơi kiểm tra quyền cuối cùng; backend vẫn phải xác thực request.

### 6.3. Từ JSON thành byte

`AssignmentJson.stringify(req)` biến Map thành chuỗi JSON; `.getBytes(UTF_8)` biến chuỗi thành byte. Lớp kiểm tra số byte không vượt 65.536.

**Ví dụ minh họa cấu trúc**, không phải một request thực đã ghi log:

```json
{
  "protocolVersion": 1,
  "requestId": "ma-yeu-cau",
  "type": "ASSIGN_LIST",
  "sessionId": "phien-da-dang-nhap",
  "roomId": "12"
}
```

### 6.4. Gửi và nhận trong `try (Socket socket = new Socket())`

Mỗi `request()` tạo một socket mới:

1. Kết nối, timeout connect 3.000 ms.
2. Đặt timeout đọc 15.000 ms.
3. Ghi độ dài bằng `writeInt`, rồi ghi JSON bytes.
4. `flush()` đẩy buffer ra stream bên dưới.
5. Đọc độ dài phản hồi bằng `readInt()`.
6. Kiểm tra độ dài; dùng `readFully()` lấy đủ payload.
7. Parse JSON và trả về Map.
8. Thoát `try` thì socket tự đóng.

`try-with-resources` là cú pháp Java giúp tài nguyên `AutoCloseable` được đóng ngay cả khi xảy ra exception.

### 6.5. Vì sao không gửi luôn trên kết nối 5000

| Kênh | Dữ liệu | Cách sử dụng |
|---|---|---|
| 5000 | Lệnh text và thông báo phòng | Kết nối lâu dài; reader nhận cả thông báo chủ động |
| 5002 | Request/response JSON bài tập | Client hiện mở socket cho từng request |

Nếu hai bộ phận cùng tự đọc một `DataInputStream` ở 5000, bộ phận chờ kết quả bài tập có thể đọc nhầm thông báo chat, hoặc ngược lại. Việc tách kênh tránh kiểu tranh đọc này.

Phía nhận tương ứng là [AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java): `serve()` đọc frame JSON và gọi `service.handle(req)`. Dù server hỗ trợ vòng lặp nhiều request trên một socket, client `AssignmentConnection` hiện chọn kiểu mở/gửi/nhận/đóng từng lần.

### 6.6. Chấm lâu có làm socket chờ 15 giây không

Thông thường request nộp bài chỉ chờ **server nhận bài và trả submission ID/trạng thái**. Việc biên dịch/chạy Java nằm ở worker. Client hỏi lại kết quả bằng các request khác.

```text
Gửi bài → nhận QUEUED + ID → đóng request socket
                    |
                    v
             worker chấm nền
                    |
                    v
Hỏi kết quả theo ID → RUNNING hoặc DONE
```

Vì vậy timeout của request mạng không đồng nghĩa với giới hạn chạy code trong sandbox.

## 7. Giải thích ClientModule và cách ghép module

File: [ClientModule.java](../client/src/main/java/vn/studyfix/client/integration/ClientModule.java).

Nội dung hợp đồng rất ngắn:

```java
public interface ClientModule {
    String tabTitle();
    JComponent view();
}
```

`interface` quy định **một module phải cung cấp được gì**, chưa phải toàn bộ logic của module.

- `tabTitle()`: trả về tên tab.
- `view()`: trả về một thành phần Swing để ghép vào UI.
- `JComponent` là lớp nền của nhiều thành phần Swing như `JPanel`.

### 7.1. Ai thực hiện hợp đồng này

[ExerciseModule.java](../client/src/main/java/vn/studyfix/client/exercise/ExerciseModule.java) khai báo:

```java
public final class ExerciseModule extends JPanel implements ClientModule
```

Hiểu là: `ExerciseModule` vừa là một panel, vừa đáp ứng hợp đồng module. `MainFrame` tạo module và gọi `exerciseModule.view()` để ghép vào tab Bài tập; tab Thi dùng thêm `contestView()` riêng của lớp đó.

**Trong source hiện tại, MainFrame ghép các tab một cách tường minh.** Chỉ khai báo `implements ClientModule` chưa làm module tự xuất hiện; vẫn phải có code khởi tạo và thêm view vào giao diện. Interface này cũng không tự quy định hàm đóng tài nguyên.

### 7.2. Ví dụ học cách hiểu interface

Đây là ví dụ minh họa, **không phải file đã tồn tại trong source**:

```java
final class DemoModule extends JPanel implements ClientModule {
    public String tabTitle() {
        return "Ví dụ";
    }

    public JComponent view() {
        return this;
    }
}
```

`return this` hợp lệ vì `DemoModule` là `JPanel`, mà `JPanel` cũng là `JComponent`.

### 7.3. ClientModuleContext và Adapter dùng để làm gì

[ClientModuleContext.java](../client/src/main/java/vn/studyfix/client/integration/ClientModuleContext.java) cho module truy cập các khả năng cần thiết:

- Lấy phiên đăng nhập bằng `session()`.
- Lấy trạng thái phòng bằng `roomState()`.
- Gửi lệnh điều khiển qua `sendControlCommand()`.
- Đăng ký nghe message/state.
- Báo lỗi lên giao diện.

[ClientModuleContextAdapter.java](../client/src/main/java/vn/studyfix/client/integration/ClientModuleContextAdapter.java) thực hiện các hàm đó bằng cách gọi sang `RoomController`.

```text
ExerciseModule
    ├── context.session()/roomState()
    │       → ClientModuleContextAdapter → RoomController
    └── connection.request(...)
            → AssignmentConnection → TCP 5002
```

**Ví dụ:** module muốn biết đang ở phòng nào thì hỏi context; muốn lấy danh sách bài thì gửi JSON qua `AssignmentConnection`. Nó không cần tự tạo lại hệ thống login hoặc tự đọc socket điều khiển.

## 8. Server nhận lệnh và xác thực như thế nào

### 8.1. `Server.acceptLoop()` nhận kết nối

File: [Server.java](../server/src/studyfix/Server.java).

```text
listener.accept()
    → có một Socket mới
    → tạo ClientHandler
    → thêm handler vào map clients
    → workers.execute(client)
```

`ConcurrentHashMap` cho phép quản lý tập kết nối đang bị nhiều luồng truy cập. Thread pool xử lý công việc thay vì tạo số thread không giới hạn. Source có giới hạn tiếp nhận 128 client ở kênh lõi; đây là giới hạn cấu hình/logic, không phải kết quả khẳng định đã đo tải 128 máy thực.

### 8.2. `ClientHandler.run()` xử lý một phiên

File: [ClientHandler.java](../server/src/studyfix/ClientHandler.java).

Một handler giữ socket, stream, identity sau login, phòng hiện tại và queue gửi riêng. Vòng đọc nhận lệnh như `/login`, `/join`, `/chat`, `/request`, `/history` rồi chuyển tới đối tượng phụ trách.

**Ví dụ:** khi nhận `/chat Xin chào`, handler kiểm tra đang có phòng, sau đó gọi logic chat của `currentRoom`. Nó không tự sửa code hoặc tự chấm Java vì hai nghiệp vụ đó có lớp khác xử lý.

### 8.3. Passwords và Store

- [Passwords.java](../server/src/studyfix/Passwords.java): băm mật khẩu bằng PBKDF2WithHmacSHA256, salt ngẫu nhiên, 600.000 vòng, khóa dẫn xuất 256 bit.
- [Store.java](../server/src/studyfix/Store.java): đọc người dùng trong SQLite và xác thực thông qua hàm kiểm tra hash.
- [Identity.java](../server/src/studyfix/Identity.java): mang thông tin tài khoản đã được server xác nhận.

**Ví dụ:** người dùng gửi mật khẩu; server lấy hash đã lưu để verify. Hash mới của cùng mật khẩu có thể khác nhau do salt khác nhau; không thể so sánh bằng cách băm một lần mới rồi so hai chuỗi hash tùy ý.

### 8.4. Hai lớp có chữ Gateway khác nhau

| File | Vai trò thực tế |
|---|---|
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | Nhận HTTP, phát static web, quản lý phiên web và route API |
| [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java) | API Java nội bộ để module khác hỏi identity, membership, quyền chủ phòng, snapshot code |

`StudyFixGateway` không mở thêm cổng mạng. Ví dụ `gateway.identity(sessionId)` tìm handler đã đăng nhập; `gateway.isTeacher(...)` kiểm tra chủ phòng theo logic server.

### 8.5. Login web đi qua đâu

```text
auth.js login()
    → api.js POST /api/auth/login
    → WebGatewayServer.login()
    → WebSession.login() kết nối TCP tới core trên cùng máy
    → gửi /login
    → ClientHandler + Store xác thực
    → gateway trả token web và identity
```

Sau đó trình duyệt gửi `Authorization: Bearer ...`. Browser không được tự chọn core session ID để trở thành tài khoản khác; gateway dựng lại session ID khi chuyển request nghiệp vụ.

## 9. Logic phòng học và rời cuộc họp

### 9.1. Tạo và tham gia phòng

Đọc:

- Web [room.js](../web-client/static/features/room/room.js): `commandCreateRoom()`, `joinRoom()`.
- HTTP [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java): `rooms()`, `roomCommand()`.
- Lõi [ClientHandler.java](../server/src/studyfix/ClientHandler.java): `createAndJoinRoom()`, `joinExistingRoom()`.
- [Server.java](../server/src/studyfix/Server.java): `createRoom()`, `findRoom()`, `deleteRoom()`.
- [RoomCode.java](../server/src/studyfix/RoomCode.java): chuẩn hóa và kiểm tra mã.

Mã phòng tự nhập được trim/chuẩn hóa Unicode, kiểm tra độ dài và ký tự điều khiển. `roomCode` để hiển thị/tìm; `roomId` nội bộ để lưu dữ liệu liên quan.

**Ví dụ:** nhập `Lớp Java A`, database tạo ID nội bộ `12`. Bài tập và lịch sử gắn ID `12`, không lấy chuỗi `Lớp Java A` làm khóa ngoại.

Danh sách phòng được server tìm/lọc và phân trang 5 phòng. Phòng trống vẫn tồn tại; chủ phòng chủ động xóa. `deleteRoom()` lưu trạng thái inactive trước khi gỡ phòng khỏi RAM và tách thành viên, tránh báo đã xóa khi database chưa ghi thành công.

### 9.2. Rời cuộc họp khác rời phòng

File [room.js](../web-client/static/features/room/room.js):

| Thao tác | Hàm | Điều gì thay đổi |
|---|---|---|
| Rời cuộc họp | `leaveMeeting()` | Dừng gọi/màn hình, đánh dấu đóng cuộc họp, về Tổng quan; giữ tham gia phòng |
| Rời phòng | `leaveRoomMembership()` | Gửi `LEAVE_ROOM`, bỏ tham gia, xóa state phụ thuộc phòng và về Tổng quan |
| Xóa phòng | Backend `Server.deleteRoom()` | Chủ phòng gỡ phòng cho mọi người; ghi inactive, giữ dữ liệu lưu trữ |

**Ví dụ:** An bấm Rời cuộc họp thì vẫn có thể bấm Vào phòng để trở lại. An bấm Rời phòng thì phải tham gia lại và có thể chuyển sang phòng khác.

### 9.3. Vì sao refresh không nên tự tạo thêm mục sidebar

[state.js](../web-client/static/core/state.js) có `rememberView()`/`restoreView()`. [lifecycle.js](../web-client/static/core/lifecycle.js) xác nhận phiên/phòng trước khi khôi phục. Main sidebar được dựng trong [render.js](../web-client/static/ui/render.js), không phải mỗi lần có `state.room` là tự thêm một mục mới.

[polling.js](../web-client/static/core/polling.js) có bộ đếm request và `invalidateRoomRequests()`. Khi người dùng rời phòng, một response cũ đến sau không được phép khôi phục lại phòng cũ.

**Ví dụ:** request A hỏi phòng 12 đang trên đường về; An bấm rời phòng; A trả về chậm. Nếu cứ gán `state.room = A.room`, UI sẽ tưởng An chưa rời. Bộ đếm request giúp bỏ response hết hiệu lực.

## 10. Logic sửa code và kiểm soát phiên bản

### 10.1. Giao thức đóng khung TCP

Đọc [FrameCodec.java](../client/src/main/java/vn/studyfix/client/net/FrameCodec.java) và [Protocol.java](../server/src/studyfix/Protocol.java).

```text
4 byte độ dài payload, big-endian
                +
N byte payload UTF-8
```

Phía ghi:

```java
out.writeInt(payload.length);
out.write(payload);
out.flush();
```

Phía đọc:

```java
int length = in.readInt();
byte[] payload = new byte[length];
in.readFully(payload);
```

Trong source thật còn có kiểm tra giới hạn độ dài trước khi cấp phát mảng.

**Vì sao cần framing?** TCP là dòng byte; một lần gửi không đồng nghĩa một lần đọc. Hai lệnh có thể được nhận dính nhau hoặc chia ra nhiều lượt đọc. Độ dài giúp receiver biết lấy đúng bao nhiêu byte cho một message.

Ví dụ `/ping` dài 5 byte ASCII. Phần đầu frame là `00 00 00 05`, rồi mới đến `/ping`. Với `chào`, số ký tự và số byte UTF-8 không bằng nhau: 4 ký tự nhưng 5 byte. Vì vậy dùng `getBytes(UTF_8).length`, không lấy `String.length()` làm độ dài mạng.

### 10.2. Quyền sửa chung ở Room

File [Room.java](../server/src/studyfix/Room.java), các hàm `requestEdit()`, `requestEditPriority()`, `releaseEdit()`, `updateCode()` và nhóm hàm xử lý draft.

- Người đang giữ quyền sửa được ghi bằng `editor`.
- Người chờ nằm trong queue.
- `token` xác nhận đúng lượt sửa.
- `version` xác nhận đúng bản code gốc.
- `draft` chứa nội dung chưa commit.

**Ví dụ:** An giữ token 8 và đang sửa v4. Giáo viên thu hồi quyền; token tăng thành 9. Một lệnh cũ của An gửi token 8 sẽ bị từ chối, dù An vẫn ở trong phòng. Nếu token đúng nhưng version đã đổi thì cũng cần kiểm tra để tránh ghi đè bản mới.

Đây là luồng sửa chung/khả năng tương thích native. Trong phiên học theo nhánh, `legacyEditAllowed()` còn kiểm tra phase; học sinh không dùng luồng này để tự sửa Master ở giai đoạn làm nhánh.

### 10.3. EditSession giữ bản nháp

[EditSession.java](../server/src/studyfix/EditSession.java) giữ các dòng gốc, vùng được chọn và thay đổi kiểu KEEP/REPLACE/DELETE. `build()` dựng lại bản code dự kiến; `complete()` kiểm tra vùng yêu cầu đã được quyết định đầy đủ chưa.

**Ví dụ:** chọn dòng 2–3 để thay. Bản gốc vẫn được giữ; draft lưu quyết định thay vùng đó. Chỉ khi hoàn tất hợp lệ và commit, code chung/version/lịch sử mới được cập nhật.

[CodeRevision.java](../server/src/studyfix/CodeRevision.java) biểu diễn một bản code đã lưu. [CodeDiff.java](../server/src/studyfix/CodeDiff.java) phục vụ mô tả thay đổi; không phải trình biên dịch hay công cụ chấm.

### 10.4. Lịch sử dài hơn một frame

`Protocol.historyFrames()` chia text dài tại ranh giới Unicode code point, thêm header `[HISTORY_PART i/n]`. [HistoryFrameAssembler.java](../client/src/main/java/vn/studyfix/client/net/HistoryFrameAssembler.java) ghép các phần theo thứ tự.

**Ví dụ:** lịch sử gồm nhiều bản source vượt 64 KiB. Server không tăng frame tùy ý; nó chia thành nhiều phần. Client chỉ đưa cho controller một message lịch sử hoàn chỉnh sau khi nhận đủ.

## 11. Logic Master và nhánh riêng

Đọc [ClassroomService.java](../server/src/studyfix/ClassroomService.java), [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java), [classroom.js](../web-client/static/features/classroom/classroom.js), [meeting-view.js](../web-client/static/features/classroom/meeting-view.js).

### 11.1. Các giai đoạn

```text
WAITING --START--> LECTURE --FORK--> BRANCHING --FREEZE--> MERGING
                     ^                  ^                    |
                     |                  |------ RESUME ------|
                     |                                       |
                     +---------------- MERGE ----------------+

Phiên đang hoạt động --END--> ENDED --NEW_SESSION--> WAITING
```

| Hành động | Hàm/nhánh backend | Ý nghĩa |
|---|---|---|
| Bắt đầu | `action()`, case START | Vào giai đoạn giảng bài |
| Chọn đề | SELECT_EXERCISE | Lấy starter từ catalog, tạo revision Master |
| Lưu Master | MASTER_SAVE | Giáo viên lưu code chung theo expectedVersion |
| Tách nhánh | FORK | Chụp Master làm gốc cho nhánh từng học sinh |
| Lưu nhánh | BRANCH_SAVE | Học sinh sửa nhánh của chính tài khoản |
| Kiểm tra nhánh | BRANCH_CHECK | Tạo bản chụp để chấm, không chấm buffer đang đổi liên tục |
| Dừng sửa | FREEZE | Chuyển BRANCHING sang MERGING |
| Chọn lời giải | MERGE | Đưa code nhánh được chọn thành revision Master mới |

### 11.2. Một ví dụ đầy đủ

1. Giáo viên có Master v4, trong phòng có An và Bình.
2. FORK tạo vòng mới, mỗi người có nhánh riêng cùng baseVersion=4.
3. An lưu nhánh lên v1 rồi kiểm tra. Snapshot chấm chứa source nhánh v1.
4. An tiếp tục sửa lên v2. Kết quả của v1 vẫn là kết quả v1; UI có thể đánh dấu stale khi so với nhánh hiện tại.
5. Giáo viên FREEZE để học sinh ngừng cập nhật nhánh.
6. Giáo viên chọn nhánh An v2 để MERGE.
7. Backend kiểm tra đúng vòng, đúng phase, đúng branchVersion và Master chưa đổi so với lúc fork.
8. Repository lưu revision/phase liên quan trong transaction, sau đó RAM và thông báo phòng được cập nhật.

MERGE ở đây là **chọn một nhánh để thay Master theo quy trình lớp học**. Source không triển khai đầy đủ hệ thống merge Git ba chiều hay tự giải mọi conflict như Git.

### 11.3. Vì sao không để mọi học sinh sửa trực tiếp Master

Nhánh riêng cho phép mỗi người giữ lời giải độc lập. Giáo viên có thể xem lưới, so sánh tiến độ và chọn lời giải, thay vì nhiều người sửa cùng một file chung rồi khó biết thay đổi thuộc về ai.

Quyền xem nhánh cũng được kiểm tra ở server: giáo viên chủ phòng xem dữ liệu cần theo dõi; học sinh chỉ xem nhánh/kết quả được phép của mình.

## 12. Logic luyện code và chấm bài

### 12.1. Đường đi khi bấm Nộp bài trên web

```text
practice.js: submitPractice(false)
    → api.js: assignment("PRACTICE_SUBMIT", ...)
    → POST /api/assignments
    → WebGatewayServer.assignmentRequest()
    → AssignmentTcpServer.handleWebRequest() [gọi hàm nội bộ]
    → AssignmentService.handle()
    → handlePractice() → createPractice()
    → PracticeRepository.create(): lưu snapshot QUEUED
    → queue.offer(GradingTask)
    → trả submission ID cho web
    → worker lấy tác vụ → DockerJavaSandbox.grade()
    → PracticeRepository.finish(): lưu verdict
    → web poll PRACTICE_RESULT theo đúng ID
```

**Điểm dễ nhầm:** web không bắt buộc mở TCP tới 5002 để gửi bài. `handleWebRequest()` là adapter gọi service trong cùng tiến trình Java. Client Swing mới sử dụng socket 5002 qua `AssignmentConnection`.

### 12.2. Tại sao luyện tập không cần vào phòng

Trong [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java), `handle()` kiểm tra identity trước, rồi có nhánh:

```java
if (type.startsWith("PRACTICE_"))
    return handlePractice(requestId, type, req, who);
```

Sau nhánh này mới đến điều kiện yêu cầu `roomId` và membership cho nghiệp vụ theo phòng.

Nghĩa là **không cần phòng nhưng vẫn phải đăng nhập**. Bài thi và bài được giao không tự được bỏ hết kiểm tra quyền chỉ vì có thêm luyện tập.

### 12.3. Catalog và đề bài

- [SeedExerciseCatalog.java](../server/src/studyfix/assignment/catalog/SeedExerciseCatalog.java): dữ liệu đề/test được nạp ban đầu.
- [Exercise.java](../server/src/studyfix/assignment/model/Exercise.java): cấu trúc đề, template/starter, public/hidden tests, reference solution.
- `publicView()` chỉ tạo phần dữ liệu an toàn để trả cho client.
- [AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java): đọc catalog từ database.

**Ví dụ:** MATH001 có đề giai thừa và test. Người học thấy đề, input/output và ví dụ công khai; lời giải tham chiếu không được đưa vào response chỉ để giao diện tự chấm.

### 12.4. Phiên bản và chống nộp trùng

[PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java), các hàm `create()`, `byKey()`, `history()`, `get()`, `progress()`.

- Phiên bản chính thức tăng theo `user_id + exercise_id`.
- Source của một submission được lưu tại lúc tiếp nhận.
- `is_run=1` là chạy thử, không thuộc lịch sử nộp chính thức.
- Khóa request giúp nhận diện retry.

**Ví dụ mất mạng:** An gửi bài, server đã lưu v3 nhưng response bị mất. Nếu An retry cùng source và cùng khóa, service trả lại lần nộp đã có. Nếu mỗi retry tạo request key mới thì server có thể hiểu thành một lần nộp mới; vì vậy web có lưu khóa của request chưa rõ kết quả.

### 12.5. Queue và worker

`createPractice()` không chờ compile/chạy tất cả test ngay trong request. Nó lưu bài rồi đưa một `GradingTask` vào queue.

`workLoop()` lấy task và phân loại: PRACTICE, PERSONAL, SHARED, CONTEST, TRIAL. Các loại này có cách lấy source/kết quả khác nhau nhưng dùng chung hạ tầng queue của AssignmentService và sandbox.

**Ví dụ:** có ba bài A, B, C và hai worker. Hai worker có thể chấm A/B; C ở trạng thái QUEUED. Việc có queue không có nghĩa tất cả bài được chạy ngay cùng lúc.

### 12.6. DockerJavaSandbox thực hiện gì

File [DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java), hàm `grade()` và `dockerBase()`.

1. Tạo thư mục tạm và ghi `Main.java`.
2. Mở container để chạy `javac`.
3. Nếu compile lỗi, trả verdict lỗi biên dịch và diagnostics có giới hạn.
4. Với từng test, mở container chạy Java và đưa input qua stdin.
5. Thu stdout/stderr có giới hạn; so sánh output đã normalize với expected output trên server.
6. Tổng hợp điểm, test đạt và verdict.
7. Dọn container/thư mục tạm theo logic của sandbox.

Source có các giới hạn Docker như không có mạng, memory 128m, CPU 0.5, pids 32, root filesystem chỉ đọc, bỏ capabilities và giới hạn thời gian/output. Đây là các giới hạn được đặt trong lệnh chạy, không phải kết quả đo dung lượng tối thiểu để cài toàn hệ thống.

**Ví dụ:** bài giai thừa in `120` cho input `5` chỉ chứng minh một test đó đúng. Nộp chính thức còn phải chạy các test khác; chỉ vượt tất cả test theo bộ đề mới nhận ACCEPTED.

**Hai Java khác nhau:** server hiện build theo cấu hình release 26; container mặc định dùng image `eclipse-temurin:21-jdk` để compile/chạy bài nộp. Đừng suy ra phiên bản Java của bài nộp từ phiên bản JDK đang chạy backend.

### 12.7. Vì sao Docker Desktop có thể trống

Sandbox dùng `docker run --rm`. Container chỉ tồn tại lúc compile/chạy test rồi được xóa. Docker Engine chạy tốt vẫn có thể đang có 0 container khi chưa chấm bài.

### 12.8. Vì sao bài đã AC vẫn xanh sau lần nộp sai

`PracticeRepository.progress()` tổng hợp lịch sử nộp chính thức. Nếu đã có verdict ACCEPTED thì trạng thái bài là AC. Các dòng history vẫn giữ verdict riêng.

```text
v1: WRONG_ANSWER → đỏ
v2: ACCEPTED    → xanh
v3: COMPILE_ERROR → đỏ

Trạng thái bài tổng hợp: đã có AC → xanh
```

`PRACTICE_RUN` chỉ chạy public tests, không được dùng để tăng thành tích AC chính thức. Lỗi SANDBOX_UNAVAILABLE/INTERNAL_ERROR được phân biệt với lỗi lời giải.

## 13. Giao bài và kỳ thi

Đọc [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java), [AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java), web [exercises.js](../web-client/static/features/exercises/exercises.js), [contests.js](../web-client/static/features/contests/contests.js).

| Loại hoạt động | Đối tượng lưu chính | Cần hiểu điều gì |
|---|---|---|
| Luyện code | Practice submission gắn user + exercise | Không cần assignment/phòng |
| Bài được giao | Assignment và submission | Có ngữ cảnh phòng, phạm vi người nhận, trạng thái/hạn nộp |
| Kiểm tra code chung | SharedCheckRun | Gắn với snapshot/version code chung |
| Bài thi | Contest và contest submission | Kiểm tra kỳ thi, bài trong kỳ thi, quyền và thời gian |
| Giáo viên thử bài | TeacherTrialRecord | Luồng thử riêng, khác thành tích học sinh |

`requireAssignmentVisible()` kiểm tra bài thuộc phòng và người dùng có được phép xem. `submit()` kiểm tra điều kiện nộp bài cá nhân. `createContest()`/`submitContest()` xử lý kỳ thi; repository tổng hợp leaderboard theo quy tắc hiện có.

**Ví dụ:** học sinh luyện MATH001 trong Luyện code đã AC không tự có nghĩa em đó đã nộp bài MATH001 trong một kỳ thi. Hai ngữ cảnh có lịch sử/quy tắc riêng.

Web dùng `state.exerciseMode` để phân biệt luyện cá nhân và công cụ bài của phòng. Trong cuộc họp có đường mở công cụ giao bài cũ. Client Swing `ExerciseModule` hiện vẫn có luồng bài được giao theo phòng; không nên mặc định nó đã có UI giống hệt trang Luyện code mới trên web.

## 14. Chat và thông báo chưa đọc

### 14.1. Tin nhắn được gửi như thế nào

Web gọi action CHAT; gateway chuyển lệnh vào core session. `ClientHandler` gọi `Room.chat()` để xử lý/phát tin theo phòng. Phần chat của lớp học còn được lưu và đọc qua [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java).

Client Swing gửi lệnh text qua `RoomService`/kết nối điều khiển. `ChatPanel` hiển thị thông báo sau khi parser/controller xử lý.

### 14.2. Chưa đọc nằm ở đâu

File web [chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js): `loadChat()`, `markChatReadIfVisible()`, `unreadChatCount()` và các hàm cập nhật badge.

- Backend trả tin có ID và danh tính người gửi.
- Frontend lưu mốc đã đọc theo tài khoản/phòng.
- Tin của chính tài khoản không được tính là tin mới từ người khác.
- Khi tab ẩn hoặc người dùng chưa thực sự xem phần mới, không đơn giản coi tất cả tin là đã đọc.

**Ví dụ:** An đóng khung chat, Bình gửi tin. Badge tăng. An gửi một tin của mình thì không tự tăng badge “tin từ người khác”. Việc mốc đã đọc được giữ trong browser không đồng nghĩa người gửi có một bảng read receipt chính thức cho từng người nhận.

Thông báo nổi dùng [toast.js](../web-client/static/ui/toast.js), giao diện chat dùng phần render trong `room.js`/`meeting-view.js`.

## 15. Mic camera và chia sẻ màn hình

### 15.1. Mic/camera trên web

Đọc [call.js](../web-client/static/features/classroom/call.js), [MeetingCalls.java](../server/src/studyfix/MeetingCalls.java), các route call trong `WebGatewayServer`.

```text
Người dùng cho phép mic/camera
    → browser lấy MediaStream
    → RTCPeerConnection chuẩn bị gửi/nhận track
    → OFFER / ANSWER / ICE qua gateway
    → các peer thương lượng đường truyền
    → browser nhận track và gắn vào video/audio
```

`MeetingCalls` giữ danh sách peer, trạng thái audio/video và inbox signaling, kiểm tra thành viên cùng phòng và giới hạn số thiết bị. Nó không phải một file ghi toàn bộ âm thanh/video vào SQLite.

**Ví dụ:** camera đã sáng trên máy An nhưng Bình chưa thấy chưa chắc là camera hỏng. Có thể SDP chưa gắn đúng track, ICE chưa kết nối hoặc browser nhận track chưa gắn vào phần tử video. Mỗi lớp có nơi debug riêng.

### 15.2. Screen share trên web

[screen-share.js](../web-client/static/features/screen-share/screen-share.js) gọi `getDisplayMedia()`, gửi START/REQUEST/OFFER/ANSWER/ICE/END và quản lý peer màn hình. Âm thanh tab chỉ có khi browser/cách chọn nguồn cung cấp audio track; yêu cầu audio trong code không đảm bảo mọi kiểu chọn “toàn màn hình” đều có system audio.

Khi rời cuộc họp, `meetingClosed` và hàm dọn media giúp không tự mở lại luồng nhận màn hình chỉ vì một thông báo cũ đến muộn.

### 15.3. Screen share native khác web

- [ScreenCaptureService.java](../client/src/main/java/vn/studyfix/client/screen/ScreenCaptureService.java): `Robot` chụp desktop, resize và mã hóa JPEG.
- [ScreenShareClient.java](../client/src/main/java/vn/studyfix/client/screen/ScreenShareClient.java): gửi/nhận packet trên 5001.
- [ScreenShareRelay.java](../server/src/studyfix/screenshare/ScreenShareRelay.java): xác thực ticket, quản lý publisher/viewer và chuyển ảnh.
- [ScreenShareWire.java](../server/src/studyfix/screenshare/ScreenShareWire.java): độ dài packet + loại packet + payload nhị phân.

**Ví dụ:** một ảnh JPEG không được gửi bằng cách ghép vào lệnh `/chat`. Nó có kênh nhị phân riêng. Native relay này cũng không tự trở thành hệ thống mic/camera WebRTC của trình duyệt.

## 16. Database lưu những gì

### 16.1. Database lõi

Mặc định launcher dùng `server/data/studyfix.db`; cấu hình có thể đổi đường dẫn.

| Nhóm bảng | Lưu gì | Code đọc/ghi chính |
|---|---|---|
| `users` | Tài khoản, password hash, tên, role | `Store`, `Passwords` |
| `rooms` | Chủ phòng, code chung/version, mã công khai, active | `Store`, `RoomCode`, `Server` |
| `revisions` | Lịch sử code chung | `Store`, `CodeRevision` |
| `classroom_sessions` | Phase/vòng/bài của phiên học | `ClassroomRepository` |
| `classroom_branches` | Nhánh riêng học sinh | `ClassroomRepository` |
| `classroom_checks` | Snapshot kiểm tra nhánh/kết quả | `ClassroomRepository` |
| `classroom_events`, `classroom_chat` | Sự kiện và chat lớp học | `ClassroomRepository` |

### 16.2. Database học tập

Mặc định `server/data/studyfix-learning.db`.

| Nhóm bảng | Lưu gì |
|---|---|
| `p3_exercises`, `p3_test_cases` | Đề và test |
| `p3_assignments`, nhóm recipients | Bài được giao và người nhận |
| `p3_submissions`, `p3_test_results` | Bài nộp cá nhân theo assignment |
| `p3_teacher_trials` và test results | Lượt thử riêng của giáo viên |
| Nhóm `p3_contests` và bài/participant/submission liên quan | Kỳ thi và kết quả |
| `p3_practice_submissions` | Luyện tập riêng, source snapshot/version/run/result |

Core và learning là hai database. Không nên tự suy ra có foreign key trực tiếp giữa `users` ở database lõi và mọi bảng ở learning; việc kiểm tra identity còn được service/gateway thực hiện.

### 16.3. Transaction dễ hiểu

Một transaction gom nhiều thay đổi thành một công việc lưu nhất quán: thành công thì commit, lỗi thì rollback.

**Ví dụ:** MERGE cần lưu revision mới và phase mới. Nếu revision đã lưu nhưng phase chưa đổi, hệ thống có thể đọc ra trạng thái nửa cũ nửa mới. `Store.classroomTransaction()`/`ClassroomRepository.change()` giải quyết việc lưu liên quan trong cùng transaction của core database.

Không được suy ra rằng mọi thay đổi trên cả hai database đều là một distributed transaction; source xử lý nhất quán trong từng ngữ cảnh lưu trữ cụ thể.

## 17. Các luồng chạy song song

| Nơi | Luồng/cơ chế | Vì sao cần |
|---|---|---|
| Swing UI | EDT | Cập nhật component đúng luồng giao diện |
| `TcpControlConnection` | Reader + writer + heartbeat | Nhận sự kiện, gửi lệnh và giữ phiên độc lập |
| `ExerciseModule` | Công việc mạng nền bằng SwingWorker | Không chặn UI trong lúc đợi API TCP |
| Core server | Accept loop + worker pool | Nhận nhiều kết nối |
| `ClientHandler` | Queue gửi riêng + writer | Một client đọc chậm không làm mọi client phải chờ ghi cùng nó |
| HTTP gateway | HTTP thread pool + scheduler | Phục vụ request và dọn phiên/heartbeat |
| `AssignmentService` | Queue + worker chấm | Tách việc chấm lâu khỏi request mạng |
| `ClassroomService` | Grader riêng cho kiểm tra nhánh | Chấm snapshot nhánh trong ngữ cảnh phiên lớp |
| Web | Promise/async/await + polling | Không khóa tương tác trong khi chờ response |

`synchronized` bảo vệ những đoạn cập nhật trạng thái cần nhìn thấy nhất quán. `volatile` giúp các luồng thấy giá trị mới, nhưng không tự làm một chuỗi nhiều thao tác thành nguyên tử. `ConcurrentHashMap` bảo vệ các thao tác map; nó cũng không tự thay toàn bộ transaction nghiệp vụ.

**Ví dụ:** đọc Master/version/draft nên cùng nhìn một thời điểm hợp lệ. `StudyFixGateway.assignmentCodeSnapshot()` giữ khóa Room để lấy bộ dữ liệu này, thay vì lấy ba trường rời rạc trong lúc luồng khác đang thay đổi.

Hai queue cần phân biệt: luyện tập/giao bài/thi dùng queue của AssignmentService; kiểm tra nhánh mới dùng grader của ClassroomService. Hai nơi cùng dùng abstraction sandbox nhưng không có nghĩa là một queue duy nhất cho toàn dự án.

## 18. Bản đồ file phía web

Tất cả đường dẫn trong bảng nằm dưới `web-client/static/`.

| File | Code/logic nên đọc | Ví dụ tác dụng |
|---|---|---|
| [index.html](../web-client/static/index.html) | Root app, toast region, tải CSS và app.js | Nơi browser bắt đầu nạp giao diện |
| [app.js](../web-client/static/app.js) | `registerPage()`, `init()` | Trang exercises chọn renderer theo exerciseMode |
| [core/state.js](../web-client/static/core/state.js) | `state`, `rememberView()`, `restoreView()` | Giữ trang, tài khoản, phòng và dữ liệu UI |
| [core/api.js](../web-client/static/core/api.js) | `api()`, `assignment()`, `command()` | Thêm Authorization và parse lỗi HTTP |
| [core/events.js](../web-client/static/core/events.js) | Delegated click/input/change/submit | Bấm nút practice-submit đi tới submitPractice |
| [core/lifecycle.js](../web-client/static/core/lifecycle.js) | `init()` | Khôi phục phiên sau refresh |
| [core/polling.js](../web-client/static/core/polling.js) | `startPolling()`, `loadPageData()`, `navigate()` | Hỏi sự kiện/kết quả và tải dữ liệu trang |
| [core/router.js](../web-client/static/core/router.js) | Registry renderer | Tên trang ánh xạ tới hàm dựng HTML |
| [core/dom.js](../web-client/static/core/dom.js) | Bind/giữ lại media elements | Không thay srcObject vô ích sau mỗi render |
| [features/auth/auth.js](../web-client/static/features/auth/auth.js) | login/logout/refreshMe | Đổi token và identity khi đăng nhập |
| [features/room/room.js](../web-client/static/features/room/room.js) | Create/join, leaveMeeting, leaveRoomMembership, history | Tách rời cuộc họp với rời phòng |
| [features/classroom/classroom.js](../web-client/static/features/classroom/classroom.js) | Load view, click, save/check branch | Nối các nút lớp học tới API phase/vòng |
| [features/classroom/meeting-view.js](../web-client/static/features/classroom/meeting-view.js) | `renderMeeting()` | Bố cục cuộc họp, grid/editor/footer |
| [features/classroom/call.js](../web-client/static/features/classroom/call.js) | Join/leave call, toggleDevice, peer negotiation | Bật mic/camera và xử lý signal |
| [features/classroom/chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js) | LoadChat, read marker, badge | Đếm tin chưa đọc theo đúng tài khoản/phòng |
| [features/screen-share/screen-share.js](../web-client/static/features/screen-share/screen-share.js) | Capture, offer/answer/ICE, cleanup | Chia sẻ/nhận màn hình web |
| [features/exercises/practice.js](../web-client/static/features/exercises/practice.js) | Load/open/submit/poll/history/draft | Trang luyện tập không cần phòng |
| [features/exercises/exercises.js](../web-client/static/features/exercises/exercises.js) | Assignment workspace, kết quả và lịch sử chung | Công cụ bài của phòng, helper kết quả được practice dùng lại |
| [features/contests/contests.js](../web-client/static/features/contests/contests.js) | Contest list/workspace/submit | Mở và nộp bài trong một kỳ thi |
| [ui/render.js](../web-client/static/ui/render.js) | Layout, overview, room list, render | Dựng sidebar và danh sách phòng |
| [ui/components.js](../web-client/static/ui/components.js) | `btn()`, `pill()`, empty state | Tạo nút và badge dùng chung |
| [ui/modal.js](../web-client/static/ui/modal.js) | Mở/đóng dialog | Xem source của một phiên bản |
| [ui/toast.js](../web-client/static/ui/toast.js) | Toast/fail/chat notification | Hiện thông báo ngắn |
| [shared/format.js](../web-client/static/shared/format.js) | `esc()`, `humanVerdict()`, `statusClass()` | Escape HTML; AC xanh, lỗi bài làm đỏ |
| [shared/crypto.js](../web-client/static/shared/crypto.js) | Random ID, SHA-256 | Khóa request và dấu nhận diện source |
| [app.css](../web-client/static/app.css), [meeting.css](../web-client/static/meeting.css) | Style | Đổi màu/bố cục không nằm trong Java service |

Một nút thường được dựng như `data-action="practice-submit"`. Browser click vào nút; `events.js` đọc action; action được chuyển tới `practiceClick()`; hàm đó gọi `submitPractice()`. Đây là cách lần theo từ giao diện tới nghiệp vụ nhanh nhất.

## 19. Bản đồ file client Swing

Các file ở `client/src/main/java/vn/studyfix/client/`.

| Nhóm/file | Nhiệm vụ |
|---|---|
| [StudyFixApplication.java](../client/src/main/java/vn/studyfix/client/StudyFixApplication.java) | Điểm chạy main |
| [config/ClientConfig.java](../client/src/main/java/vn/studyfix/client/config/ClientConfig.java) | Host/cổng/timeout mặc định |
| [net/TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | Kết nối TCP điều khiển lâu dài |
| [net/FrameCodec.java](../client/src/main/java/vn/studyfix/client/net/FrameCodec.java) | Encode/decode frame text |
| [net/HistoryFrameAssembler.java](../client/src/main/java/vn/studyfix/client/net/HistoryFrameAssembler.java) | Ghép lịch sử nhiều frame |
| [net/ConnectionListener.java](../client/src/main/java/vn/studyfix/client/net/ConnectionListener.java) | Callback nhận message/mất kết nối |
| [protocol/WireCommands.java](../client/src/main/java/vn/studyfix/client/protocol/WireCommands.java) | Dựng chuỗi lệnh gửi server |
| [protocol/ServerMessageParser.java](../client/src/main/java/vn/studyfix/client/protocol/ServerMessageParser.java) | Chuyển chuỗi thô thành ServerMessage |
| [protocol/ServerMessage.java](../client/src/main/java/vn/studyfix/client/protocol/ServerMessage.java) | Kiểu thông báo đã parse |
| [controller/RoomController.java](../client/src/main/java/vn/studyfix/client/controller/RoomController.java) | Nhận event, cập nhật RoomState và báo observer |
| [service/AuthService.java](../client/src/main/java/vn/studyfix/client/service/AuthService.java) | Login và tạo SessionInfo |
| [service/RoomService.java](../client/src/main/java/vn/studyfix/client/service/RoomService.java) | Tạo/vào/rời phòng, chat, thành viên |
| [service/CodeCollaborationService.java](../client/src/main/java/vn/studyfix/client/service/CodeCollaborationService.java) | Các thao tác xin quyền, chọn/sửa/commit draft |
| [model/SessionInfo.java](../client/src/main/java/vn/studyfix/client/model/SessionInfo.java) | Thông tin tài khoản/phiên ở client |
| [model/RoomState.java](../client/src/main/java/vn/studyfix/client/model/RoomState.java) | Trạng thái phòng hiển thị, không phải authority thay server |
| [model/MemberInfo.java](../client/src/main/java/vn/studyfix/client/model/MemberInfo.java) | Thông tin một thành viên |
| [integration/ClientModule.java](../client/src/main/java/vn/studyfix/client/integration/ClientModule.java) | Hợp đồng tên tab và component |
| [integration/ClientModuleContext.java](../client/src/main/java/vn/studyfix/client/integration/ClientModuleContext.java) | Khả năng dùng chung được cung cấp cho module |
| [integration/ClientModuleContextAdapter.java](../client/src/main/java/vn/studyfix/client/integration/ClientModuleContextAdapter.java) | Nối context sang RoomController |
| [exercise/AssignmentConnection.java](../client/src/main/java/vn/studyfix/client/exercise/AssignmentConnection.java) | JSON TCP 5002, mỗi request có socket riêng |
| [exercise/ExerciseModule.java](../client/src/main/java/vn/studyfix/client/exercise/ExerciseModule.java) | UI bài tập/thi và gọi request nền |
| [exercise/protocol/AssignmentJson.java](../client/src/main/java/vn/studyfix/client/exercise/protocol/AssignmentJson.java) | Encode/decode JSON phía client native |
| [screen/ScreenCaptureService.java](../client/src/main/java/vn/studyfix/client/screen/ScreenCaptureService.java) | Robot chụp màn hình và JPEG |
| [screen/ScreenShareClient.java](../client/src/main/java/vn/studyfix/client/screen/ScreenShareClient.java) | Kết nối relay màn hình |
| [screen/ScreenShareWire.java](../client/src/main/java/vn/studyfix/client/screen/ScreenShareWire.java) | Packet nhị phân phía client |
| [util/SwingExecutor.java](../client/src/main/java/vn/studyfix/client/util/SwingExecutor.java) | Helper chạy việc nền/cập nhật UI |

Các panel giao diện:

| File trong `ui/` | Hiển thị/xử lý giao diện |
|---|---|
| [LoginFrame.java](../client/src/main/java/vn/studyfix/client/ui/LoginFrame.java) | Nhập server/tài khoản, gọi AuthService |
| [MainFrame.java](../client/src/main/java/vn/studyfix/client/ui/MainFrame.java) | Ghép các panel/tab, đăng ký listener kết nối |
| [RoomControlsPanel.java](../client/src/main/java/vn/studyfix/client/ui/RoomControlsPanel.java) | Các nút thao tác phòng |
| [SharedCodePanel.java](../client/src/main/java/vn/studyfix/client/ui/SharedCodePanel.java) | Code chung và điều khiển sửa |
| [ChatPanel.java](../client/src/main/java/vn/studyfix/client/ui/ChatPanel.java) | Chat |
| [MembersPanel.java](../client/src/main/java/vn/studyfix/client/ui/MembersPanel.java) | Thành viên |
| [HistoryPanel.java](../client/src/main/java/vn/studyfix/client/ui/HistoryPanel.java) | Revision và lịch sử code |
| [ScreenSharePanel.java](../client/src/main/java/vn/studyfix/client/ui/ScreenSharePanel.java) | Điều khiển/chờ/hiển thị chia sẻ màn hình |
| [ExerciseIntegrationPanel.java](../client/src/main/java/vn/studyfix/client/ui/ExerciseIntegrationPanel.java) | Panel hỗ trợ tích hợp bài tập; xem chỗ khởi tạo để biết có đang được gắn vào UI hay không |
| [ActivityPanel.java](../client/src/main/java/vn/studyfix/client/ui/ActivityPanel.java) | Dòng hoạt động/thông báo |
| [UiStyle.java](../client/src/main/java/vn/studyfix/client/ui/UiStyle.java) | Màu/font/style dùng chung |

**Cách đọc một panel:** tìm constructor xem nó nhận controller/context nào; tìm `addActionListener` để biết nút gọi service nào; tìm observer để biết nó cập nhật từ message nào. Đừng chỉ nhìn tên panel rồi đoán nghiệp vụ đã được nối.

## 20. Bản đồ file phía server

### 20.1. Lõi và HTTP

| File | Chứa logic gì |
|---|---|
| [Studyfix.java](../server/src/studyfix/Studyfix.java) | Main mở ServerAdmin |
| [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java) | UI quản lý, startServices/shutdown, theo dõi dịch vụ |
| [Server.java](../server/src/studyfix/Server.java) | Listener 5000, clients/rooms, tạo/tìm/xóa phòng, vòng đời core |
| [ClientHandler.java](../server/src/studyfix/ClientHandler.java) | Một phiên TCP, parse lệnh, login, membership, outbound queue |
| [Protocol.java](../server/src/studyfix/Protocol.java) | Frame UTF-8 64 KiB, chia lịch sử dài |
| [Client.java](../server/src/studyfix/Client.java) | Client console để tương tác/kiểm tra kênh lõi |
| [Room.java](../server/src/studyfix/Room.java) | Thành viên, code, quyền sửa, draft, broadcast, revision |
| [RoomCode.java](../server/src/studyfix/RoomCode.java) | Chuẩn hóa/kiểm tra mã phòng |
| [Store.java](../server/src/studyfix/Store.java) | SQLite lõi, schema, login, room/revision và transaction |
| [Passwords.java](../server/src/studyfix/Passwords.java) | Băm/verify mật khẩu |
| [Identity.java](../server/src/studyfix/Identity.java) | Danh tính đã xác thực |
| [EditSession.java](../server/src/studyfix/EditSession.java) | Draft theo dòng/vùng |
| [CodeRevision.java](../server/src/studyfix/CodeRevision.java) | Một revision code |
| [CodeDiff.java](../server/src/studyfix/CodeDiff.java) | Mô tả khác biệt code |
| [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java) | Cầu nối nội bộ tới identity, membership, snapshot và gửi sự kiện |
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | HTTP/HTTPS, route, phiên web, room APIs, adapter assignment, signaling |
| [MeetingCalls.java](../server/src/studyfix/MeetingCalls.java) | Peer/inbox/rate/giới hạn signaling cuộc gọi |
| [ClassroomService.java](../server/src/studyfix/ClassroomService.java) | Máy trạng thái lớp học, nhánh, kiểm tra, merge và phân quyền |
| [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java) | Dữ liệu phiên/nhánh/check/chat/event trong core DB |

### 20.2. Module assignment

| File | Chứa logic gì |
|---|---|
| [AssignmentModuleConfig.java](../server/src/studyfix/assignment/AssignmentModuleConfig.java) | Cổng, database, queue/worker, image Docker |
| [AssignmentEnabledServerMain.java](../server/src/studyfix/assignment/AssignmentEnabledServerMain.java) | Main console chạy đủ dịch vụ |
| [transport/AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java) | Nhận TCP 5002, đọc JSON, gọi service; adapter cho web |
| [protocol/AssignmentWire.java](../server/src/studyfix/assignment/protocol/AssignmentWire.java) | Frame JSON TCP 64 KiB |
| [protocol/AssignmentJson.java](../server/src/studyfix/assignment/protocol/AssignmentJson.java) | Parse/stringify JSON và helper lấy field |
| [service/AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | Kiểm tra quyền, request type, nhận bài, queue/worker và nghiệp vụ thi/giao bài/luyện tập |
| [persistence/AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java) | Catalog, assignment, submission, contest, trial và thống kê |
| [persistence/PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | Snapshot/version/history/progress của luyện tập |
| [catalog/SeedExerciseCatalog.java](../server/src/studyfix/assignment/catalog/SeedExerciseCatalog.java) | Đề/test seed hiện có |
| [grading/GradingSandbox.java](../server/src/studyfix/assignment/grading/GradingSandbox.java) | Interface hàm grade |
| [grading/DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java) | Compile/run/test trong Docker và giới hạn tài nguyên |
| [integration/SharedRoomCodeProvider.java](../server/src/studyfix/assignment/integration/SharedRoomCodeProvider.java) | Hợp đồng lấy snapshot code chung |
| [integration/GatewaySharedRoomCodeProvider.java](../server/src/studyfix/assignment/integration/GatewaySharedRoomCodeProvider.java) | Thực hiện hợp đồng snapshot bằng StudyFixGateway |

Các model trong `assignment/model/`:

- [Exercise.java](../server/src/studyfix/assignment/model/Exercise.java): một đề và test.
- [ExerciseMode.java](../server/src/studyfix/assignment/model/ExerciseMode.java): loại bài.
- [AssignmentRecord.java](../server/src/studyfix/assignment/model/AssignmentRecord.java): bài được giao.
- [SubmissionRecord.java](../server/src/studyfix/assignment/model/SubmissionRecord.java): bài nộp gắn assignment.
- [SharedCheckRun.java](../server/src/studyfix/assignment/model/SharedCheckRun.java): lượt kiểm tra code chung.
- [ContestSubmissionRecord.java](../server/src/studyfix/assignment/model/ContestSubmissionRecord.java): bài nộp trong kỳ thi.
- [TeacherTrialRecord.java](../server/src/studyfix/assignment/model/TeacherTrialRecord.java): lượt thử giáo viên.
- [GradingResult.java](../server/src/studyfix/assignment/model/GradingResult.java): verdict/điểm/test/feedback trả về từ sandbox.

Model mô tả **dữ liệu**; service quyết định **được làm gì**; repository quyết định **lưu/đọc như thế nào**. Không nên dồn ba trách nhiệm đó vào một panel UI.

### 20.3. Module relay màn hình native

| File | Chứa logic gì |
|---|---|
| [screenshare/ScreenShareRelay.java](../server/src/studyfix/screenshare/ScreenShareRelay.java) | Ticket ngắn hạn, publisher/viewer và chuyển frame |
| [screenshare/ScreenShareWire.java](../server/src/studyfix/screenshare/ScreenShareWire.java) | HELLO/FRAME/STOP/ERROR/STATUS và framing nhị phân |

## 21. Cách lần theo và debug một chức năng

### 21.1. Ví dụ nút Nộp bài không hoạt động

Lần theo thứ tự sau, đừng sửa Docker trước khi biết request đã tới backend chưa:

1. **Nút:** `practice.js` dựng `data-action="practice-submit"` chưa?
2. **Event:** `events.js` có nhận action và gọi `practiceClick()` không?
3. **Source:** `state.workCode` có đúng code vừa nhập không?
4. **Mạng:** Network của browser có POST `/api/assignments` với type PRACTICE_SUBMIT không?
5. **HTTP:** lỗi 401 là phiên; lỗi action không hợp lệ có thể do server JAR chưa cập nhật.
6. **Service:** `handlePractice()`/`createPractice()` có trả lỗi source, key hoặc queue không?
7. **DB:** có snapshot submission với đúng source không?
8. **Worker:** task có chuyển từ QUEUED sang RUNNING không?
9. **Docker:** compile/run có lỗi môi trường hay lỗi bài làm?
10. **Hiển thị:** `pollPractice()` có hỏi đúng ID và cập nhật đúng bài đang mở không?

### 21.2. Ví dụ client Swing không nhận kết quả đăng nhập

```text
LoginFrame
 → AuthService.login
 → TcpControlConnection.connect
 → sendAndAwait
 → outbound queue / writeLoop
 → FrameCodec.write
 → Protocol.receive ở server
 → ClientHandler xử lý /login
 → Store xác thực
 → LOGIN_OK hoặc LOGIN_FAILED
 → reader / waiter
 → AuthService tạo SessionInfo
```

Breakpoint nên đặt ở đầu `AuthService.login()`, `TcpControlConnection.writeLoop()`, nhánh `/login` của server và `readLoop()` phía client. Không in mật khẩu/token thật vào log chỉ để debug.

### 21.3. Ví dụ sửa màu AC

- Điều kiện bài đã AC: `PracticeRepository.progress()`.
- Payload trả về: `AssignmentService.handlePractice()`.
- Chọn class hiển thị dòng bài: `practice.js`.
- Chữ verdict/class badge dùng chung: `shared/format.js`.
- Màu thực tế: CSS.

Nếu backend trả FAILED thì đổi màu CSS thành xanh chỉ che lỗi logic, không làm bài được chấm đúng.

### 21.4. Tìm theo câu hỏi

| Muốn biết/sửa | Bắt đầu ở đâu |
|---|---|
| Bấm nút nào gọi hàm nào trên web | Tìm `data-action`, rồi `events.js` |
| Giao diện tại sao hiện một trạng thái | Renderer + state + response API |
| Ai được tạo/xóa phòng | `Server.createRoom/deleteRoom`, `Room.isOwner` |
| Tại sao không được nộp bài | `AssignmentService` trước khi tới sandbox |
| Tại sao chấm WA/CE/TLE | `DockerJavaSandbox.grade` và source snapshot |
| Dữ liệu lưu đâu | `Store`, `ClassroomRepository`, `AssignmentRepository`, `PracticeRepository` |
| Vì sao một socket bị đóng | `ClientHandler.closeReason`, `TcpControlConnection.fail` |
| Vì sao UI Swing đứng | Kiểm tra gọi mạng trên EDT, SwingWorker/background |
| Vì sao web cứ ghi đè code mới | Request guard, version, draft và logic polling |
| Vì sao máy khác không vào được | Server bind/cổng/firewall/IP trước khi sửa UI |

### 21.5. Đọc test để hiểu điều kiện đúng

| Chức năng | Test nên đọc |
|---|---|
| Protocol, quyền sửa, DB/recovery | `server/test/studyfix/IntegrationTest.java`, `EditSessionTest.java`, `RecoveryTest.java`, `HistoryTest.java` |
| HTTP, phiên web, bảo vệ API | `WebGatewayIntegrationTest.java` |
| Master/branch/merge | `ClassroomIntegrationTest.java` |
| Mic/camera signaling | `MeetingCallIntegrationTest.java` |
| Phòng và mã tự chọn | `RoomLifecycleIntegrationTest.java` |
| Luyện tập, quyền riêng tư, snapshot | `PracticeIntegrationTest.java` |
| Java thật trong Docker | `PracticeDockerIntegrationTest.java` |
| Client Swing | `client/src/test/java/.../ClientSelfTest.java`, `exercise/ExerciseModuleSelfTest.java` |
| Logic UI luyện tập | `web-client/tests/check_practice.mjs` |
| Rời cuộc họp/refresh | `web-client/tests/check_meeting_navigation.mjs` |
| Chat, media và screen audio | Các `check_chat_notifications.mjs`, `check_meeting.mjs`, `check_screen_audio.mjs` |

Test dùng giả lập DOM/HTTP/WebRTC chỉ chứng minh các tình huống giả lập tương ứng. Test Docker thật mới kiểm tra thực thi Java trong container; chúng cũng không tự chứng minh camera/âm thanh LAN giữa hai máy đã được nghiệm thu thực tế.

## 22. Thứ tự học và bài tự kiểm tra

### 22.1. Lộ trình đọc đề xuất

1. **Nhìn hệ thống:** chương 1–4; biết client/server/database và bốn cổng.
2. **Hiểu truyền dữ liệu:** FrameCodec/Protocol, TcpControlConnection, AssignmentConnection.
3. **Hiểu client:** AuthService → parser → RoomController → một panel đơn giản như ChatPanel.
4. **Hiểu server:** acceptLoop → ClientHandler → Room → Store.
5. **Hiểu nghiệp vụ lớp:** ClassroomService.action/view và transaction ở ClassroomRepository.
6. **Hiểu chấm bài:** AssignmentService.handle/createPractice/workLoop → PracticeRepository → DockerJavaSandbox.
7. **Hiểu web:** app → lifecycle/state → events → api → feature/polling → render.
8. **Cuối cùng:** media, native relay, recovery, hạn chế và test.

Đừng bắt đầu bằng việc học thuộc một file UI lớn như ExerciseModule. Hãy chọn một hành động nhỏ và theo nó từ đầu tới cuối.

### 22.2. Các từ khóa Java nên hiểu ngay trong source

| Từ khóa/kiểu | Cách hiểu | Nơi thấy |
|---|---|---|
| `interface` | Hợp đồng các hàm phải cung cấp | ClientModule, GradingSandbox |
| `implements` | Một lớp thực hiện hợp đồng | ExerciseModule implements ClientModule |
| `extends` | Kế thừa một lớp | ExerciseModule extends JPanel |
| `final` | Không gán lại tham chiếu/kế thừa/ghi đè tùy ngữ cảnh | Field connection, class/service model |
| `record` | Cách gọn để biểu diễn một nhóm dữ liệu | SessionInfo, Identity, GradingResult |
| `Map` | Tra cứu theo key | clients theo session ID |
| `BlockingQueue` | Queue có thao tác chờ thread-safe | outbound và queue chấm |
| `CompletableFuture` | Một kết quả sẽ có sau | Waiter trong sendAndAwait |
| Lambda `x -> ...` | Truyền một hành vi/hàm ngắn | Matcher phản hồi, callback UI |
| `try-with-resources` | Tự đóng socket/connection/stream | AssignmentConnection, repository |
| `synchronized` | Khóa khi nhiều thread cùng đụng trạng thái | Room và các đoạn snapshot |
| `AtomicBoolean` | Đổi cờ nguyên tử | Tránh đóng socket nhiều lần |
| `PreparedStatement` | SQL có tham số bind | Repository, không ghép input người dùng trực tiếp vào SQL |

`final Map` vẫn có thể thêm/xóa phần tử; `final` chỉ ngăn gán field đó sang một Map khác. Đây là điểm thường bị hiểu nhầm khi đọc map rooms/clients.

### 22.3. Câu hỏi tự trả lời

1. Vì sao phải `readFully()` sau `readInt()`? Vì cần lấy đủ số byte của frame, không dựa vào một lần read bất kỳ.
2. Ai quyết định role? Identity đã được server xác thực, không phải chữ giáo viên hiển thị trên giao diện.
3. `send()` thành công có nghĩa đã lưu DB chưa? Chưa; lệnh mới vào queue gửi.
4. Tại sao dùng submission ID? Để hỏi đúng kết quả/snapshot dù editor đã thay đổi.
5. Rời cuộc họp có xóa phòng không? Không; đó là thao tác khác với rời phòng và xóa phòng.
6. Người học chạy thử đạt public tests đã được tính AC chưa? Chưa tính thành tích chính thức.
7. Docker trống có nghĩa Docker tắt không? Không; container của grader có vòng đời ngắn và `--rm`.
8. Thêm một ClientModule mới đã tự có tab chưa? Chưa; phải xem đoạn khởi tạo và ghép UI.
9. Web gọi service bài tập có luôn đi qua TCP 5002 không? Không; gateway có adapter gọi nội bộ.
10. Bản mới trên web có tự xuất hiện trong Swing không? Không; chúng có renderer/controller riêng.

## 23. Những giới hạn cần hiểu đúng từ source

Phần này ghi rõ điều đang thấy trong mã, để khi học hoặc bảo vệ bạn không mô tả quá mức triển khai.

- **Client web và Swing không đồng nhất tính năng.** Luồng Luyện code mới nằm ở web; Swing ExerciseModule vẫn yêu cầu phòng ở helper request và có UI bài được giao.
- **HTTP web dùng polling.** Không nên gọi nó là WebSocket nếu source đang dùng các request `/api/events`, `/api/call/signals`, `/api/screen/signals` theo chu kỳ.
- **Signaling khác media.** Server có thể trả roster/signal đúng nhưng đường truyền WebRTC vẫn cần thương lượng/kết nối ở browser. Source có STUN; không nên tự tuyên bố đã có SFU/TURN nếu chưa thấy cấu hình/triển khai tương ứng.
- **Mỗi loại version có phạm vi riêng.** Master v2, branch v2 và practice v2 không phải cùng một bản ghi.
- **Giới hạn thread/client không phải số máy đã đo.** Đọc hằng số giúp hiểu thiết kế; muốn báo cáo khả năng chịu tải phải có thực nghiệm riêng.
- **Bản nháp khác source đã nộp.** Đừng lấy buffer editor để hiển thị như lịch sử một lần nộp cũ.
- **Có điểm cần rà soát thêm khi đọc practice.js hiện tại:** object ban đầu có `drafts` và `requests` là Map, nhưng `scope()` chưa đặt lại hai Map này trong `Object.assign` khi đổi owner. `draftFor()` ưu tiên Map trong RAM theo exercise ID. Vì vậy không nên khẳng định chỉ các field đang được reset đã bảo đảm mọi cache RAM tách tài khoản; tình huống đổi tài khoản rồi mở cùng bài cần được kiểm tra/bổ sung. Tài liệu này ghi nhận đúng source, không tự thay đổi logic ứng dụng.
- **Matcher và parser có giả định về chuỗi.** Ví dụ RoomController có regex lấy tên phòng bằng `\S+`; nếu muốn native UI hỗ trợ mã có khoảng trắng đầy đủ, cần đối chiếu cả message state và lệnh/phản hồi, không chỉ đổi một nhãn UI.

## 24. Tra nhanh các vị trí code quan trọng

| File | Hàm / khai báo | Dòng bắt đầu |
|---|---|---:|
| [Studyfix.java](../server/src/studyfix/Studyfix.java) | `main` | 16 |
| [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java) | `startServices` | 90 |
| [Server.java](../server/src/studyfix/Server.java) | `acceptLoop` | 62 |
| [Server.java](../server/src/studyfix/Server.java) | `deleteRoom` | 98 |
| [ClientHandler.java](../server/src/studyfix/ClientHandler.java) | `run` | 168 |
| [Protocol.java](../server/src/studyfix/Protocol.java) | `receive` | 32 |
| [Protocol.java](../server/src/studyfix/Protocol.java) | `send` | 42 |
| [Protocol.java](../server/src/studyfix/Protocol.java) | `historyFrames` | 12 |
| [Room.java](../server/src/studyfix/Room.java) | `requestEdit` | 202 |
| [Room.java](../server/src/studyfix/Room.java) | `updateCode` | 258 |
| [Store.java](../server/src/studyfix/Store.java) | `transaction` | 53 |
| [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java) | `assignmentCodeSnapshot` | 44 |
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | `assignmentRequest` | 421 |
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | `roomCommand` | 331 |
| [ClassroomService.java](../server/src/studyfix/ClassroomService.java) | `action` | 63 |
| [ClassroomService.java](../server/src/studyfix/ClassroomService.java) | `view` | 30 |
| [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | `handle` | 37 |
| [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | `handlePractice` | 139 |
| [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | `createPractice` | 178 |
| [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | `workLoop` | 330 |
| [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | `create` | 34 |
| [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | `finish` | 49 |
| [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | `progress` | 66 |
| [DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java) | `grade` | 38 |
| [DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java) | `dockerBase` | 170 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `connect` | 51 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `send` | 65 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `sendAndAwait` | 73 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `readLoop` | 87 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `writeLoop` | 104 |
| [TcpControlConnection.java](../client/src/main/java/vn/studyfix/client/net/TcpControlConnection.java) | `fail` | 113 |
| [AssignmentConnection.java](../client/src/main/java/vn/studyfix/client/exercise/AssignmentConnection.java) | `request` | 12 |
| [ClientModule.java](../client/src/main/java/vn/studyfix/client/integration/ClientModule.java) | `ClientModule` | 6 |
| [ClientModule.java](../client/src/main/java/vn/studyfix/client/integration/ClientModule.java) | `tabTitle` | 7 |
| [ClientModule.java](../client/src/main/java/vn/studyfix/client/integration/ClientModule.java) | `view` | 8 |
| [AuthService.java](../client/src/main/java/vn/studyfix/client/service/AuthService.java) | `login` | 18 |
| [RoomController.java](../client/src/main/java/vn/studyfix/client/controller/RoomController.java) | `accept` | 43 |
| [lifecycle.js](../web-client/static/core/lifecycle.js) | `init` | 8 |
| [api.js](../web-client/static/core/api.js) | `assignment` | 44 |
| [polling.js](../web-client/static/core/polling.js) | `loadPageData` | 153 |
| [polling.js](../web-client/static/core/polling.js) | `startPolling` | 188 |
| [room.js](../web-client/static/features/room/room.js) | `leaveMeeting` | 22 |
| [room.js](../web-client/static/features/room/room.js) | `leaveRoomMembership` | 30 |
| [practice.js](../web-client/static/features/exercises/practice.js) | `submitPractice` | 74 |
| [practice.js](../web-client/static/features/exercises/practice.js) | `pollPractice` | 93 |
| [practice.js](../web-client/static/features/exercises/practice.js) | `scope` | 21 |

### Cách sử dụng mục tra nhanh

Chọn đúng nhánh giao diện bạn đang dùng trước: Swing hay web. Mở file, tìm tên hàm và đặt breakpoint ở nơi chuyển giao trách nhiệm. Một lần đọc hiệu quả nên trả lời được bốn câu: **ai gọi hàm này, đầu vào là gì, nó thay trạng thái nào, kết quả được đưa cho ai**.

Khi hiểu một luồng như đăng nhập hoặc nộp bài theo bốn câu đó, bạn có thể đọc tiếp các chức năng khác bằng cùng cách, thay vì học thuộc từng dòng rời rạc.
