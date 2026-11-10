# Phân tích chi tiết source StudyFix: web, server, giao thức và luồng nghiệp vụ

> Cập nhật backend sau lần phân tích: xem [Sửa độ tin cậy và đồng bộ server](SERVER_RELIABILITY_FIXES_VI.md) về khóa membership, dọn heartbeat, retry ghi kết quả chấm, khóa đăng nhập, tải chi tiết Server Manager và transaction tạo version. Chỉ mục số dòng phía cuối là ảnh chụp tại lúc lập, có thể thay đổi sau các bản sửa này.

**Đối chiếu source ngày 04/11/2026.** Tài liệu mô tả chương trình hiện có, không phải đề xuất kiến trúc mới. Các con số giới hạn là cấu hình/hằng số trong source, không phải kết quả đo tải. Các tình huống minh họa không phải dữ liệu thật của người dùng.

**Phạm vi hiện tại:** client web trong `web-client/` và backend Java trong `server/`. Thư mục ứng dụng Swing `client/` đã được xóa theo yêu cầu; không coi các lớp từng nằm trong thư mục đó là thành phần còn tồn tại. Backend vẫn có giao thức TCP, console client và relay native. Server Manager cũng dùng Swing nhưng nằm trong backend và vẫn được giữ.

## Mục lục

1. [Bức tranh toàn hệ thống](#1-bức-tranh-toàn-hệ-thống)
2. [Server khởi động và vận hành](#2-server-khởi-động-và-vận-hành)
3. [Giao thức và hợp đồng dữ liệu](#3-giao-thức-và-hợp-đồng-dữ-liệu)
4. [Đăng nhập và quản lý phiên](#4-đăng-nhập-và-quản-lý-phiên)
5. [Phòng học và điều hướng](#5-phòng-học-và-điều-hướng)
6. [Lớp học online và các vai trò](#6-lớp-học-online-và-các-vai-trò)
7. [Master nhánh riêng và Merge](#7-master-nhánh-riêng-và-merge)
8. [Code cộng tác theo quyền sửa](#8-code-cộng-tác-theo-quyền-sửa)
9. [Mic camera và chia sẻ màn hình](#9-mic-camera-và-chia-sẻ-màn-hình)
10. [Chat và tin chưa đọc](#10-chat-và-tin-chưa-đọc)
11. [Luyện tập code từ giao diện tới kết quả](#11-luyện-tập-code-từ-giao-diện-tới-kết-quả)
12. [Docker chấm bài như thế nào](#12-docker-chấm-bài-như-thế-nào)
13. [Giao bài kỳ thi và bảng xếp hạng](#13-giao-bài-kỳ-thi-và-bảng-xếp-hạng)
14. [Database transaction và xử lý đồng thời](#14-database-transaction-và-xử-lý-đồng-thời)
15. [Bản đồ toàn bộ file production](#15-bản-đồ-toàn-bộ-file-production)
16. [Kiểm thử giới hạn và cách debug](#16-kiểm-thử-giới-hạn-và-cách-debug)
17. [Chỉ mục hàm và số dòng tự tra trong IDE](#17-chỉ-mục-hàm-và-số-dòng-tự-tra-trong-ide)

## 1. Bức tranh toàn hệ thống

```text
Giáo viên / học sinh mở trình duyệt
    |
    | tải HTML/CSS/JS; gửi HTTP/HTTPS API tới cổng mặc định 8080
    v
WebGatewayServer
    +-- static files -> giao diện
    +-- WebSession -> TCP nội bộ 5000 -> Server / ClientHandler -> Room / Store
    +-- /api/classroom -> ClassroomService -> ClassroomRepository -> core DB
    |                         +-> queue kiểm tra nhánh riêng -> sandbox
    +-- /api/assignments -> AssignmentTcpServer.handleWebRequest()
    |                         -> AssignmentService -> repository -> learning DB
    |                                  +-> queue chấm -> worker -> sandbox
    +-- call/screen signaling -> thông tin thiết lập WebRTC

Trình duyệt A <======= media WebRTC =======> Trình duyệt B
```

Không phải mọi API đều đi qua TCP 5000. Các lệnh phòng như chat được chuyển sang giao thức lõi; các thao tác lớp học và bài tập có đường gọi Java trực tiếp. `AssignmentTcpServer.handleWebRequest()` được gọi trong cùng JVM, không mở socket 5002 cho request web.

**Vai trò các lớp:** gateway nhận/chuyển yêu cầu; service quyết định nghiệp vụ; Room giữ trạng thái phòng; repository/Store đọc ghi dữ liệu; worker xử lý công việc chấm lâu; sandbox chạy code người dùng. Giao diện chỉ phản ánh trạng thái mà backend cho phép, không phải nguồn xác nhận quyền.

## 2. Server khởi động và vận hành

### 2.1. Các điểm bắt đầu

| File | Hàm/điểm đọc | Nhiệm vụ |
|---|---|---|
| [run-server-manager.ps1](../server/run-server-manager.ps1) | Nội dung script | Kiểm tra điều kiện chạy, build JAR và mở ứng dụng quản lý server |
| [Studyfix.java](../server/src/studyfix/Studyfix.java) | `main()` | Chuyển sang `ServerAdmin.main()` |
| [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java) | `main()`, `startServices()` | Dựng cửa sổ quản lý, khởi động dịch vụ ở tác vụ nền |
| [AssignmentEnabledServerMain.java](../server/src/studyfix/assignment/AssignmentEnabledServerMain.java) | `main()` | Cách chạy backend bằng console |
| [AssignmentModuleConfig.java](../server/src/studyfix/assignment/AssignmentModuleConfig.java) | `load()` | Đọc system properties: cổng, database, queue, worker, Docker image |

Mặc định: TCP lõi 5000; relay native 5001; API bài tập TCP 5002; web 8080; queue chấm AssignmentService 32; worker 2; image `eclipse-temurin:21-jdk`. Các giá trị có thể thay bằng cấu hình. Không được nhầm JDK chạy server với JDK bên trong container chấm.

### 2.2. Server lõi có những gì?

Trong [Server.java](../server/src/studyfix/Server.java):

- Constructor tạo `Store`, mở `ServerSocket`, tạo `ScreenShareRelay`, đọc phòng đang hoạt động từ database vào map `rooms`. Nếu khởi tạo lỗi, các tài nguyên đã mở được dọn.
- `start()` tạo thread listener chạy `acceptLoop()`.
- `acceptLoop()` chờ socket, kiểm tra trạng thái/giới hạn kết nối, tạo `ClientHandler`, lưu vào map `clients`, chuyển cho executor chạy.
- `createRoom()` xác nhận role giáo viên, chuẩn hóa mã, ghi database rồi mới thêm Room vào RAM.
- `findRoom()` tìm theo mã công khai, còn đường tương thích với ID nội bộ.
- `deleteRoom()` kiểm tra chủ phòng, ghi hết hiệu lực vào database trước, rồi loại phòng khỏi RAM và tách các thành viên.
- `snapshot()` cung cấp thông tin vận hành; `log()` giữ nhật ký giới hạn; `close()` đóng dịch vụ và tài nguyên.

Map `clients` giữ **kết nối đã được server tiếp nhận**, không phải bảng tài khoản. Map `rooms` giữ các đối tượng phòng đang được backend quản lý. Thread pool lõi có trần 256, tiếp nhận tối đa 128 kết nối lõi; handler còn dùng writer riêng. Đây không phải bằng chứng hỗ trợ 128 người đồng thời bật camera.

### 2.3. Mỗi ClientHandler làm gì?

Trong [ClientHandler.java](../server/src/studyfix/ClientHandler.java), `run()` đọc từng frame bằng `Protocol.receive()`, phân tích lệnh rồi gọi xử lý. Handler giữ identity, member/session ID và phòng hiện tại của kết nối.

`send()` đưa thông điệp vào `outbound`; `writeLoop()` lấy ra và ghi socket. Queue gửi có giới hạn 64, giúp quản lý trường hợp client đọc chậm. `/ping` làm mới heartbeat; quá hạn 20 giây sẽ đóng kết nối. `/login` gọi `Store.login()`; các lệnh khác yêu cầu đã đăng nhập. Các thao tác như `/join`, `/chat`, `/request`, `/update`, `/history` được chuyển vào nghiệp vụ phòng.

**Ví dụ:** hai tài khoản vào cùng phòng không tạo hai bản Room độc lập; hai handler tham chiếu tới cùng phòng được quản lý ở server. Những đoạn cập nhật dùng chung cần được đồng bộ.

### 2.4. HTTP gateway hoạt động thế nào?

[WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) dùng HTTP server của JDK. Constructor cấu hình HTTP/HTTPS, executor, đường dẫn tài nguyên và `ClassroomService`. `start()` mở nhận request và công việc dọn phiên. `handle()` là cửa xử lý chung; `handleApi()` phân tuyến API; `serveStatic()` trả file giao diện.

Gateway ưu tiên bản `web-client/dist` nếu có index, nếu không dùng `web-client/static`. Do đó khi sửa giao diện mà không thấy thay đổi, cần biết gateway đang phục vụ thư mục nào. HTTPS phụ thuộc cấu hình keystore, không phải cứ bật cổng 8080 là tự có HTTPS.

## 3. Giao thức và hợp đồng dữ liệu

### 3.1. Có những kênh nào?

| Kênh | Định dạng | Nơi xử lý | Dùng cho web hiện tại? |
|---|---|---|---|
| Trình duyệt → gateway | HTTP/HTTPS, JSON body/response | `core/api.js`, `WebGatewayServer` | Có |
| Gateway → lõi | TCP có length prefix, payload UTF-8 dạng lệnh | `WebSession`, `Protocol`, `ClientHandler` | Có, nội bộ backend |
| Client ngoài → bài tập 5002 | TCP length prefix + JSON | `AssignmentWire`, `AssignmentTcpServer` | Web gọi hàm trực tiếp thay vì socket |
| Mic/camera/màn hình web | WebRTC, signaling JSON qua HTTP | `call.js`, `screen-share.js`, gateway | Có |
| Relay màn hình native 5001 | Packet nhị phân có loại và payload ảnh | `ScreenShareWire`, `ScreenShareRelay` | Không phải luồng media web |

### 3.2. Framing TCP: vì sao có 4 byte độ dài?

TCP là dòng byte. Một lần gửi không đảm bảo bên nhận đọc đúng một thông điệp; dữ liệu có thể bị chia hoặc ghép. [Protocol.java](../server/src/studyfix/Protocol.java) đặt ranh giới bằng:

```text
[4 byte int, big-endian: số byte payload][payload UTF-8]
```

`send()` mã hóa UTF-8, kiểm tra tối đa 65.536 byte, khóa output stream để các thread không ghi xen header/payload, gọi `writeInt()`, `write()` và `flush()`.

`receive()` đọc `readInt()`, từ chối độ dài âm hoặc quá giới hạn, cấp mảng byte và `readFully()` đủ payload rồi mới giải mã. Không dùng số ký tự Java làm số byte UTF-8.

**Ví dụ:** payload `/ping` gồm 5 byte ASCII; header là `00 00 00 05`. Nếu mạng chuyển header và payload thành ba đoạn thì `readFully()` vẫn chờ đủ. Một chuỗi tiếng Việt có thể nhiều byte hơn số ký tự nhìn thấy.

`historyFrames()` tách lịch sử dài thành các phần có header `[HISTORY_PART i/n]`, chừa dung lượng header và tách đúng ranh giới Unicode code point. Đây là phân mảnh ở tầng ứng dụng; không phải tự tạo lại cơ chế packet của TCP.

### 3.3. Lệnh TCP lõi và JSON bài tập khác nhau

Lõi dùng lệnh chuỗi như `/login`, `/join`, `/chat`, `/update`. Bài tập dùng đối tượng JSON có `type`, `requestId`, `sessionId`, `roomId` và các trường nghiệp vụ.

[AssignmentWire.java](../server/src/studyfix/assignment/protocol/AssignmentWire.java) đóng/mở frame JSON; [AssignmentJson.java](../server/src/studyfix/assignment/protocol/AssignmentJson.java) parse/stringify và đọc trường. `AssignmentTcpServer.serve()` đọc frame, parse JSON, gọi service, trả JSON được đóng frame. Socket bài tập có timeout và giới hạn handler.

Phản hồi nghiệp vụ bài tập được tạo bởi `AssignmentService.ok()`/`error()`:

```json
{"requestId":"...","status":"OK","data":{}}
```

Hoặc `status: "ERROR"`, `errorCode`, `message`. Dấu `...` chỉ minh họa giá trị động. `requestId` để đối chiếu request; không phải khóa chống nộp trùng. Chống nộp trùng dùng `idempotencyKey` ở nghiệp vụ tương ứng.

### 3.4. Web API có những tuyến chính?

| Tuyến | Hàm xử lý / đích | Ý nghĩa |
|---|---|---|
| `GET /api/health` | `handleApi()` | Tình trạng/cổng dịch vụ |
| `POST /api/auth/login` | `login()` | Tạo phiên web dựa trên xác thực lõi |
| `POST /api/auth/logout` | `closeSession()` | Đóng phiên và tài nguyên liên quan |
| `POST /api/auth/departing` | Scheduler trong `handleApi()` | Báo trang rời đi, có khoảng chờ để refresh không bị đóng ngay |
| `GET /api/me` | `handleApi()` | Danh tính, peer, phòng hiện tại |
| `GET /api/rooms` | `rooms()` | Danh sách/tìm kiếm/phân trang phòng |
| `GET /api/rooms/current` | `currentRoom()` | Snapshot phòng hiện tại |
| `GET /api/rooms/current/history` | `roomHistory()` | Lịch sử phiên bản |
| `GET /api/rooms/current/chat` | `ClassroomRepository.chatStatus()` | Chat theo mốc ID |
| `POST /api/rooms/current/command` | `roomCommand()` | Thao tác phòng và code lõi |
| `GET /api/events` | `events()` | Sự kiện kết nối sau mốc đã nhận |
| `GET/POST /api/classroom` | `ClassroomService.view()`/`action()` | Trạng thái và hành động lớp học |
| `GET /api/classroom/branches/{userId}` | `branchDetail()` | Code đầy đủ và lượt kiểm tra của nhánh được phép xem |
| `GET /api/classroom/checks/{id}` | `checkDetail()` | Snapshot của lượt kiểm tra |
| `GET /api/classroom/rounds/{round}` | `roundReport()` | Báo cáo một vòng |
| `POST /api/assignments` | `assignmentRequest()` | Các type catalog/assignment/practice/contest |
| `/api/call/signal`, `/api/call/signals` | `callSignal()`, `MeetingCalls.poll()` | Gửi/poll signaling mic/camera |
| `/api/screen/signal`, `/api/screen/signals` | `screenSignal()`, `pollScreenSignals()` | Gửi/poll signaling chia sẻ màn hình |

`core/api.js.api()` thêm bearer token, serialize JSON, đọc JSON response và chuyển lỗi thành exception. HTTP 401 phát sự kiện để luồng auth dọn phiên. `assignment()` lấy `data` từ envelope nghiệp vụ; `command()` gửi action phòng.

### 3.5. Gửi được chưa chắc xử lý thành công

Nhiều action trong `roomCommand()` chuyển thành lệnh TCP rồi trả **202**: đã chuyển yêu cầu. Kết quả xử lý tới sau qua sự kiện/polling. Riêng những action xử lý trực tiếp như rời phòng/xóa phòng có phản hồi hoàn tất theo nhánh riêng; không áp dụng quy tắc 202 cho mọi action.

**Ví dụ:** nhận 202 khi gửi lệnh cập nhật không chứng minh version hợp lệ; lõi vẫn có thể từ chối vì version/token cũ. UI phải đồng bộ kết quả sau đó.

## 4. Đăng nhập và quản lý phiên

```text
auth.js.login()
  -> POST /api/auth/login
  -> WebGatewayServer.login()
  -> WebSession.login(core.port(), username, password)
  -> socket 127.0.0.1:5000, Protocol.send('/login ...')
  -> ClientHandler.run() -> Store.login() -> Passwords
  <- LOGIN_OK chứa session, userId, role, displayName
  -> tạo token web + peerId, giữ socket lõi
  <- web lưu phiên và tải dữ liệu
```

`Passwords` dùng PBKDF2 có salt; không lưu password dạng đọc lại nguyên văn. `Identity` là tài khoản đã xác thực. `sessionId`/member ID là phiên TCP; web token là phiên HTTP; `peerId` là định danh thiết bị signaling. Không dùng những giá trị này thay cho nhau.

`WebSession.readLoop()` nhận thông điệp TCP, bỏ `/pong`, đưa các thông điệp khác vào buffer sự kiện có ID tăng. `eventsAfter()` lấy phần sau cursor; buffer sự kiện có trần 500, không phải nhật ký vô hạn. Gateway gửi ping mỗi 8 giây. Phiên web có TTL 2 phút theo hoạt động HTTP và tác vụ cleanup định kỳ.

Khi `pagehide`, frontend báo departing; gateway hẹn đóng sau 8 giây. Request mới gọi `touch()` hủy tác vụ departing còn chờ. Điều này phục vụ refresh; không phải cam kết mọi kiểu đóng trình duyệt đều gửi thông báo thành công.

**Phân quyền:** ẩn nút ở web là tiện ích UI. Backend vẫn xác minh identity, membership, chủ phòng, phase/version theo từng nghiệp vụ. Giáo viên không tự động có quyền điều khiển mọi phòng do người khác tạo.

## 5. Phòng học và điều hướng

Các file chính: [room.js](../web-client/static/features/room/room.js), [render.js](../web-client/static/ui/render.js), [state.js](../web-client/static/core/state.js), [polling.js](../web-client/static/core/polling.js), `WebGatewayServer`, `Server`, `RoomCode`, `Room`, `Store`.

### 5.1. Tạo, tìm và vào phòng

`commandCreateRoom()` gửi mã do người dùng nhập. Backend chuẩn hóa qua `RoomCode`, kiểm tra giáo viên/điều kiện phòng hiện tại, tạo dữ liệu và tham gia theo luồng lõi. Mã công khai dài 1–80 ký tự hợp lệ, được chuẩn hóa Unicode; ID nội bộ là khóa liên kết dữ liệu riêng. Database bảo vệ điều kiện mã hoạt động không trùng.

`loadRooms()` gửi từ khóa/trang. `rooms()` lấy danh sách, chuẩn hóa tìm kiếm rồi phân trang **5 phòng/trang**. Tìm kiếm thực hiện trước phân trang; không chỉ dò năm dòng đang hiển thị. `renderRoomListCompact()` dựng danh sách, nút vào/rời/xóa tùy quyền và trạng thái.

### 5.2. Rời cuộc họp và rời phòng

- `leaveMeeting()` dừng call/screen, đóng view họp, quay về overview nhưng giữ membership lõi.
- `leaveRoomMembership()` gửi `LEAVE_ROOM`; backend dọn peer, kết thúc share liên quan, gọi `ClientHandler.leaveCurrentRoom()`, trả hoàn tất. Frontend dọn trạng thái phòng.
- Xóa phòng do chủ phòng chủ động; phòng trống không tự mất. `Server.deleteRoom()` ghi archive/expire trước rồi mới công bố thay đổi RAM; lịch sử không bị hiểu thành xóa sạch vật lý.

Sau refresh, `lifecycle.init()`, `restoreView()` và luồng tải phòng xác nhận phiên/trạng thái rồi khôi phục màn hình. Request cũ có thể về sau khi người dùng chuyển trang; các guard danh tính/phòng/generation ở module liên quan ngăn áp dụng nhầm. Không nên giải thích mọi state frontend đều tự khôi phục chỉ vì có localStorage.

## 6. Lớp học online và các vai trò

### 6.1. Các file chính

| File | Hàm/điểm xử lý | Công việc |
|---|---|---|
| [classroom.js](../web-client/static/features/classroom/classroom.js) | `loadClassroom()`, `classroomInput()`, `classroomClick()`, `sendAction()`, `save()`, `runCheck()` | Đồng bộ trạng thái, lưu nháp, gọi hành động |
| [meeting-view.js](../web-client/static/features/classroom/meeting-view.js) | `renderMeeting()` và helper | Bố cục họp, grid code, editor, panel và toolbar |
| [ClassroomService.java](../server/src/studyfix/ClassroomService.java) | `view()`, `action()` | Quy tắc phiên học và quyền thao tác |
| [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java) | `session()`, `branches()`, `change()`, `saveBranch()`, `createCheck()`, `result()` | Lưu/đọc phiên, nhánh, kiểm tra và sự kiện |
| [Room.java](../server/src/studyfix/Room.java) | `snapshot()`, `publishClassroomRevision()`, `classroomNotice()` | Master đang chạy, version, thông báo sau lưu |

### 6.2. Giáo viên và học sinh nhìn thấy gì?

Giáo viên **chủ phòng** điều khiển phase, chọn bài lỗi, xem lưới các nhánh và kết quả kiểm tra, mở code đầy đủ, chọn lời giải thành Master. Học sinh xem Master và đề, sửa nhánh của chính mình khi được phép, xem lịch sử kiểm tra được quyền xem.

`ClassroomService.view()` lọc dữ liệu theo quyền: chủ phòng nhận preview giới hạn của các nhánh; học sinh nhận nhánh của mình. Muốn xem chi tiết, `branchDetail()` kiểm tra quyền rồi trả code đầy đủ. Không phải tất cả học sinh đều nhận source của mọi bạn rồi frontend mới giấu đi.

`view()` còn tạo nhánh cho học sinh vào muộn khi đang BRANCHING và chưa có nhánh, với giới hạn 128 nhánh. Vì vậy một hàm mang tên view ở đây có thể ghi dữ liệu trong trường hợp tạo nhánh muộn, không hoàn toàn là hàm đọc thuần túy.

### 6.3. Trạng thái phiên học

```text
WAITING --START--> LECTURE --FORK--> BRANCHING --FREEZE--> MERGING
                     ^                    ^                  |
                     |                    +-----RESUME-------+
                     +------------------- MERGE -------------+

Phiên đang hoạt động --END--> ENDED --NEW_SESSION--> WAITING
```

`phase()` kiểm tra bước hiện tại; `sameRound()` kiểm tra vòng; nhánh chủ phòng kiểm tra thêm `expectedPhase` và `round` trong request để chặn nút từ giao diện cũ. `idle()` yêu cầu không còn draft/lượt sửa lõi đang hoạt động trước những thay đổi cần ổn định Master.

## 7. Master nhánh riêng và Merge

### 7.1. Chọn đề và tạo Master

Giáo viên mở catalog `DEBUG_COLLAB` bằng `classroom-pick-exercise`, chọn bài rồi gửi `SELECT_EXERCISE`. `action()` yêu cầu LECTURE, chủ phòng, version Master đúng và trạng thái sửa rảnh. `requireExercise()` xác nhận bài thuộc DEBUG_COLLAB. Service lấy `starterCode`, tạo `CodeRevision` mới và ghi cùng session/event bằng `repo.change()`.

`MASTER_SAVE` cũng tạo revision từ nội dung giáo viên gửi; code không rỗng và tối đa 32.000 byte UTF-8. Không phải mỗi ký tự gõ đều trở thành revision Master.

### 7.2. FORK thực hiện gì?

`ClassroomService.action()` ở case `FORK`:

1. Yêu cầu đang LECTURE, Master đúng version, không còn draft/lượt sửa lõi và đã chọn bài lỗi hợp lệ.
2. Tạo session BRANCHING, tăng round và lấy version Master làm `baseVersion`.
3. Gom học sinh online theo userId để tránh hai phiên của một tài khoản sinh hai nhánh trong cùng vòng.
4. Từ chối nếu chưa có học sinh phù hợp.
5. `branch()` sao chép code Master, đặt version nhánh 0 và baseVersion tương ứng.
6. Lưu session, nhánh và event; sau đó thông báo trạng thái phòng.

**Ví dụ minh họa:** Master v4 → vòng 2, nhánh An v0/base4 và Bình v0/base4. An lưu thành v1 không làm Bình thành v1 và không tự tăng Master.

### 7.3. Lưu nhánh và tự kiểm tra

`classroomInput()` cập nhật nháp, lưu vào sessionStorage theo ngữ cảnh và đặt debounce 1.200 ms cho nhánh. Khi hết chờ, frontend xác nhận vẫn đúng phòng/vòng/phase rồi gọi `save("branch")`. Nếu điều kiện tự kiểm tra cho phép, khoảng cách đủ và không đang chấm, gọi `runCheck()`.

`save()` giữ code và `baseVersion` của nháp. Nếu đã có một lượt lưu đang chạy, nó chờ rồi xử lý phần thay đổi còn lại; không tự coi mọi nội dung gõ trong lúc đợi đã được lưu. `runCheck()` lưu nhánh trước, sau đó gửi `BRANCH_CHECK` với version đã xác nhận.

Backend `BRANCH_SAVE` kiểm tra STUDENT, BRANCHING, round, đúng nhánh tài khoản, `expectedVersion == branch.version`. Chỉ khi code khác mới tăng version. Nếu hai tab sửa cùng nhánh, tab có version cũ bị từ chối thay vì ghi đè im lặng.

### 7.4. Kiểm tra nhánh không phải luyện tập độc lập

`BRANCH_CHECK` không lấy tùy ý source mới trong request để chấm; nó tạo `Check` từ **code nhánh đã lưu**, gắn room/round/user/branchVersion/exercise. Service chặn đang chấm hoặc gửi quá sát dưới 3 giây, lưu QUEUED, rồi đưa vào grader riêng có **1 worker, queue 32**.

`grade()` ghi RUNNING, gọi `sandbox.grade()`, lưu DONE và kết quả; lỗi thành JUDGE_ERROR theo nhánh xử lý. Queue đầy được xử lý rõ, không để bản ghi QUEUED giả vờ sẽ chắc chắn được chấm.

`resultStale` cho biết kết quả gần nhất thuộc version nhánh cũ. Snapshot trong Check vẫn giữ nguyên. Ví dụ kiểm tra v2 xong rồi lưu v3 thì kết quả v2 không chứng minh v3 đúng.

### 7.5. FREEZE và MERGE xử lý chính xác thế nào?

`FREEZE` chuyển BRANCHING → MERGING, làm những lần lưu nhánh sau không còn hợp lệ. Nó không có nghĩa mọi bản nháp chưa gửi trên máy học sinh đã được server lưu. `RESUME` cho phép quay lại BRANCHING cùng vòng.

Giáo viên bấm một ô → frontend gọi `GET /api/classroom/branches/{userId}` → nhận code và version → hiện nút chọn nhánh. Nút Merge giữ userId, version nhánh, round, version Master đang xem. Khi xác nhận, `sendAction("MERGE", ...)` gửi các giá trị này.

Backend kiểm tra lần lượt:

1. Phiên kết nối còn là thành viên, người gọi là chủ phòng.
2. expectedPhase/round khớp; phase phải MERGING.
3. Không có draft/lượt sửa lõi đang hoạt động.
4. `expectedVersion` khớp Master hiện tại.
5. Master hiện tại vẫn bằng `session.baseVersion` lúc tách nhánh.
6. Nhánh được chọn thuộc vòng hiện tại; `branchVersion` vẫn đúng.

Sau đó `revision()` tạo Master mới version +1 từ **toàn bộ code của nhánh được chọn**, ghi tác giả thao tác, parentVersion, lý do và diff; session quay về LECTURE. `repo.change()` lưu liên quan trong transaction; chỉ sau đó `room.publishClassroomRevision()` công bố trạng thái RAM/thông báo.

**Điểm cần hiểu đúng:** không có điều kiện bắt buộc nhánh phải AC trong case MERGE hiện tại. Giáo viên chủ động chọn. Đây cũng không phải Git three-way merge: không tự trộn dòng của nhiều nhánh, không có engine giải quyết conflict kiểu Git.

**Ví dụ:** nhánh An v3 được chọn từ Master v4, các kiểm tra đều hợp lệ → tạo Master v5 chứa code An v3 → quay lại giảng. Nếu An đã thành v4 từ lúc giáo viên xem, request mang branchVersion 3 sẽ bị chặn.

## 8. Code cộng tác theo quyền sửa

Source vẫn giữ một cơ chế sửa code chung theo lượt bên cạnh workflow nhánh. Không được gộp hai cơ chế thành cùng một thao tác.

`Room` quản lý editor, FIFO queue xin quyền, token lượt sửa, version, draft và recoverable draft. `requestEdit()` xin lượt; `requestEditPriority()` cho quyền ưu tiên theo điều kiện; `canEdit()`/các guard xác nhận người giữ lượt và token. Trạng thái lớp học còn hạn chế đường sửa lõi, tránh học sinh dùng lệnh cũ sửa Master trong giai đoạn nhánh.

Luồng nguyên khối: `UPDATE_CODE` ở gateway → `/update version token code` → `ClientHandler` → `Room.updateCode()`. Lưu cần kiểm tra cả token và version: token xác nhận lượt, version xác nhận bản code nền. Có đúng một thứ không đủ.

Luồng sửa theo dòng: chọn vùng/dòng → tạo `EditSession` từ code gốc/version → áp dụng KEEP/REPLACE/DELETE → xem draft → finish hoặc cancel. `EditSession` giữ danh sách dòng gốc và thay đổi riêng, hỗ trợ vùng liên tục hoặc dòng chọn; `complete()` xác nhận lựa chọn đã xử lý; `build()` dựng code kết quả. Dựa trên code gốc giúp số dòng không lệch sau mỗi thao tác xóa.

`CodeDiff.between()`/`display()` dùng biểu diễn thay đổi trong revision. Diff để giải thích lịch sử không đồng nghĩa thuật toán tự merge nhiều người. `CodeRevision` là dữ liệu phiên bản; `Store` lưu revision; `Room.showHistory()`/`showRevision()` trả lịch sử được phép xem.

## 9. Mic camera và chia sẻ màn hình

### 9.1. Mic/camera

[call.js](../web-client/static/features/classroom/call.js):

- `joinCall()` tham gia, có thể nghe trước mà không cần bật thiết bị.
- `capture()` xin thiết bị; `toggleDevice()` bật/tắt và cập nhật trạng thái/track.
- `makePeer()` tạo RTCPeerConnection; `bindSenders()` gắn hoặc điều chỉnh track gửi.
- `pollCall()` tuần tự hóa polling; `pollCallOnce()` cập nhật roster và xử lý OFFER/ANSWER/ICE.
- `leaveCall()` dừng track, đóng peer, dọn trạng thái, tùy chọn báo LEAVE.

Gateway `callSignal()` kiểm tra phòng và chuyển JOIN/STATE/LEAVE hoặc tín hiệu tới `MeetingCalls`. `join()` giới hạn **16 thiết bị gọi/phòng**; `signal()` chỉ cho gửi tới peer cùng phòng, giới hạn payload/tần suất/queue; `poll()` trả roster và tín hiệu sau cursor; `prune()` loại peer hết hoạt động.

```text
A xin mic/camera → A gửi JOIN
A/B poll roster → tạo peer
A gửi OFFER → gateway → B nhận → trả ANSWER
A/B trao đổi ICE → thiết lập đường media WebRTC
Track của A → peer connection → phần tử audio/video ở B
```

`core/dom.js.bindVideoElements()` gắn đúng stream vào phần tử media. Render phải bảo toàn media element/stream để tránh mỗi lần polling lại cắt cuộc gọi. Giới hạn call là thiết bị, khác số tài khoản, số phòng hay số nhánh.

### 9.2. Chia sẻ màn hình và âm thanh

[screen-share.js](../web-client/static/features/screen-share/screen-share.js): `startScreenShare()` gọi API capture của trình duyệt; `offerScreenTo()` tạo kết nối tới người nhận; `sendSignal()` chuyển tín hiệu HTTP; `receiveScreenSignal()` xử lý offer/answer/ICE và đổi người trình bày; `stopScreenShare()` dừng track và cleanup. `playScreenAudio()` phục vụ trường hợp autoplay audio bị trình duyệt chặn.

Âm thanh hệ thống/YouTube phụ thuộc audio track mà trình duyệt thực sự cung cấp khi chọn bề mặt chia sẻ. Có video track không có nghĩa có audio track. Luồng web này không dùng JPEG relay 5001. Source có STUN, chưa có triển khai TURN riêng hay SFU; không khẳng định kết nối media thành công ở mọi mạng/NAT.

## 10. Chat và tin chưa đọc

Gửi chat: sự kiện frontend → `command("CHAT", {text})` → `roomCommand()` kiểm tra nội dung → `/chat ...` qua TCP nội bộ → `ClientHandler` → `Room.chat()` → lưu chat qua `ClassroomRepository` rồi broadcast.

Đọc chat: `/api/rooms/current/chat` hoặc trạng thái lớp học cung cấp dữ liệu; [chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js) tải/cập nhật tin, so mốc ID và marker tài khoản/phòng. Tin của mình không tăng unread như tin từ người khác. Đánh dấu đã đọc cần xét khung chat đang thấy, focus/visibility và vị trí cuối danh sách. `toast.js` hiển thị popup; badge được cập nhật trên các vị trí UI liên quan.

Không nên gọi cơ chế frontend này là server read-receipt cho từng người nhận. Popup tin đến và bộ đếm chưa đọc là hai trạng thái khác nhau. Marker trên trình duyệt cũng không đồng nghĩa một bảng xác nhận đã đọc đồng bộ mọi thiết bị.

## 11. Luyện tập code từ giao diện tới kết quả

### 11.1. Những file nào tham gia?

| File | Hàm chủ đạo | Vai trò trong luồng |
|---|---|---|
| [app.js](../web-client/static/app.js) | Đăng ký renderer trang exercises | Chọn luyện tập hoặc công cụ bài của phòng theo mode |
| [events.js](../web-client/static/core/events.js) | Listener click/input/change | Bắt nút, editor và nạp file |
| [practice.js](../web-client/static/features/exercises/practice.js) | `loadPracticeList()`, `openPractice()`, `submitPractice()`, `pollPractice()` | Danh sách, workspace, nháp, gửi và theo dõi bài |
| [api.js](../web-client/static/core/api.js) | `assignment()` | Đóng JSON gửi `/api/assignments` |
| [polling.js](../web-client/static/core/polling.js) | Hook practice, `loadPageData()`, `startPolling()` | Tải trang và cập nhật nền |
| [exercises.js](../web-client/static/features/exercises/exercises.js) | `renderSubmissionResult()` | Dùng chung phần hiển thị kết quả |
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | `assignmentRequest()` | Whitelist type, gắn danh tính thật, bỏ yêu cầu room với PRACTICE |
| [AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java) | `handleWebRequest()` | Adapter gọi service trực tiếp |
| [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | `handle()`, `handlePractice()`, `createPractice()`, `workLoop()` | Xác thực, quy tắc, tạo công việc, chấm |
| [AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java) | `listExercises()`, `getExercise()` | Đọc ngân hàng và test |
| [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | `create()`, `running()`, `finish()`, `history()`, `progress()` | Snapshot/version/kết quả cá nhân |
| [DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java) | `grade()` | Biên dịch và chạy test |
| [format.js](../web-client/static/shared/format.js) | Verdict/status formatter | Tên trạng thái và màu hiển thị |

### 11.2. Mở danh sách và mở đề

`loadPracticeList()` gửi PRACTICE_LIST gồm query/topic/difficulty/mode/filterStatus/page. `handlePractice()` đọc catalog, ghép progress theo userId, tìm không phân biệt dấu/chữ hoa thường trên ID/tên, lọc rồi trả **10 bài/trang**. Phân trang này khác danh sách phòng 5/trang.

`openPractice()` giữ nháp bài cũ, lấy PRACTICE_GET, chọn template/starter hoặc nháp đã có, tải PRACTICE_SUBMISSIONS và kết quả gần nhất nếu có. `PRACTICE_GET` chỉ trả publicView và publicTests; hidden input/expected không gửi nguyên cho người làm bài.

Luyện tập không cần phòng: `handle()` xác thực identity rồi xử lý PRACTICE trước nhánh yêu cầu membership. Giáo viên và học sinh dùng cùng giao diện nhưng tiến độ/lịch sử thuộc từng tài khoản.

### 11.3. Nháp và nạp code từ file

`practiceInput()`/`savePracticeDraft()` lưu nội dung theo bài; localStorage dùng khóa gắn userId, còn selection dùng sessionStorage. Các request lấy danh sách/đề/lịch sử có số thứ tự để loại response cũ.

Listener `work-code-file` trong events.js kiểm tra file `.java`/`.txt`, giới hạn 32.000 byte, đọc nội dung, hỏi trước khi ghi đè nháp đang sửa, cập nhật editor và nháp. **Nạp file chưa nộp bài**. Chỉ bấm chạy/nộp mới tạo yêu cầu backend tương ứng.

### 11.4. submitPractice() làm gì?

1. Chặn khi chưa chọn bài hoặc đang gửi; kiểm tra source không rỗng, không vượt 32.000 byte UTF-8.
2. Giữ source hiện tại làm snapshot gửi, giữ exerciseId và owner của thao tác.
3. Tìm/tạo idempotency key theo bài và loại run/submit; lưu trước khi gửi để có thể retry.
4. Bật busy, lưu nháp và gọi PRACTICE_RUN hoặc PRACTICE_SUBMIT.
5. Khi có response, kiểm tra vẫn đúng tài khoản, lưu submissionId vào pending nếu đang chờ/chấm.
6. Tải lại lịch sử khi nộp chính thức, cập nhật progress/kết quả, bỏ busy.

Gateway loại các trường sessionId/roomId/requestId do client gửi, gắn lại danh tính từ WebSession. Không cho frontend tự chọn userId để nộp thay người khác.

### 11.5. createPractice() ở backend làm gì?

- Kiểm tra idempotency key có nội dung và dài tối đa 128; code hợp lệ, không NUL và tối đa 32.000 byte.
- `practice.byKey(userId,key)` tìm request đã có. Nếu bài/loại chạy/source không khớp thì từ chối khóa bị dùng sai; nếu khớp thì trả lại bản ghi, không tạo version mới.
- PRACTICE_RUN chỉ lấy public tests; nộp chính thức lấy bộ test của bài. Từ chối khi không có test phù hợp.
- Kiểm tra queue có chỗ, `practice.create()` lưu bản ghi rồi đưa `GradingTask` loại PRACTICE vào queue. Nếu offer thất bại sau lưu thì kết thúc bằng lỗi theo nhánh xử lý.

`PracticeRepository.create()` tạo ID, snapshot source, is_run và version. Version nộp chính thức tăng theo **user + exercise**; run dùng version 0. Database có unique constraint hỗ trợ chống trùng key/version. Không lấy số hàng hiển thị trong UI làm version.

### 11.6. Worker và lấy kết quả

`workLoop()` chờ `queue.take()`, phân loại PRACTICE, cập nhật RUNNING, đọc bản ghi/exercise, chọn public-only nếu run, gọi `sandbox.grade()` và `finish()`. Exception được chuyển thành kết quả lỗi dịch vụ theo cơ chế hiện có.

`pollPractice()` duyệt các submission đang pending, gửi PRACTICE_RESULT. Backend xác nhận bản ghi thuộc user hiện tại; không được lấy source/kết quả của tài khoản khác chỉ vì biết ID. Sau khi hoàn tất, frontend cập nhật lịch sử/progress và bỏ pending.

`finish()` chỉ cập nhật bản ghi còn QUEUED/RUNNING để không ghi đè kết quả đã kết thúc. `recover()` phục vụ phục hồi công việc chưa hoàn tất khi AssignmentService khởi động; việc lưu trong SQLite không có nghĩa queue RAM tự bền vững.

### 11.7. Màu bài và lịch sử có ý nghĩa gì?

`progress()` tính trạng thái cá nhân, ưu tiên đã từng AC, sau đó các trạng thái chờ/sai/lỗi theo logic repository. Run không tạo thành tích AC chính thức. History là các lần nộp chính thức, 10 phiên bản/trang; source không gửi kèm mọi dòng, chỉ lấy khi mở kết quả/snapshot được phép xem.

Ví dụ minh họa: v1 WA → v2 AC → v3 WA. Danh sách bài vẫn xanh vì đã giải; dòng v3 vẫn đỏ vì lần đó sai. DONE là đã xử lý xong, không đồng nghĩa ACCEPTED. Lỗi Docker/hạ tầng không nên bị hiểu thành lời giải sai.

## 12. Docker chấm bài như thế nào

`GradingSandbox` là hợp đồng chấm. `DockerJavaSandbox.grade()` là triển khai thực tế; nó không chạy code người dùng bằng cách gọi trực tiếp trong JVM server.

1. Kiểm tra source/test; tạo thư mục tạm và ghi `Main.java`.
2. Tạo container compile chạy `javac -encoding UTF-8 Main.java`; đọc mã thoát và diagnostic.
3. Nếu compile lỗi, trả COMPILE_ERROR; nếu môi trường Docker không khởi chạy được, phân biệt sandbox unavailable.
4. Mỗi test tạo container chạy `java ... Main`, cung cấp input qua stdin và thu stdout/stderr có giới hạn.
5. Chuẩn hóa output bằng `normalize()` rồi so với expected; tính số test đạt và điểm theo trọng số.
6. Xác định verdict: tất cả đạt → ACCEPTED; nếu không, xét giới hạn output/bộ nhớ/thời gian/runtime hoặc WRONG_ANSWER theo thứ tự trong code.
7. Tạo kết quả với feedback và thông tin test được phép công khai; cleanup thư mục tạm trong finally.

`dockerBase()` cấu hình `--pull=never`, `--rm`, network none, memory/swap 128m, CPU 0.5, pids 32, filesystem read-only, drop capabilities, no-new-privileges và tmpfs. Code chạy test được mount read-only; JVM test có `-Xmx64m`. `runProcess()` đọc output đồng thời và áp deadline; `killContainer()` xử lý container cần kết thúc.

stdout giới hạn 512 KiB, stderr 16 KiB, diagnostic 12 KiB theo hằng số. Image phải có sẵn do `--pull=never`. Exit 137 được source coi là trường hợp có thể vượt bộ nhớ; không nên mô tả đây là bằng chứng tuyệt đối rằng mọi exit 137 đều do OOM.

**Giới hạn suy luận:** AC chứng minh đạt bộ test đã thiết kế, không phải chứng minh toán học đúng mọi input. Giới hạn container giảm rủi ro nhưng không phải chứng nhận sandbox an toàn tuyệt đối. Tài liệu này không tự tạo số liệu tốc độ chấm hay khả năng chịu tải.

## 13. Giao bài kỳ thi và bảng xếp hạng

### 13.1. Bài được giao vẫn có logic riêng

[exercises.js](../web-client/static/features/exercises/exercises.js) giữ công cụ assignment trong phòng, mở bài/kết quả, thử bài giáo viên và tiến độ. `AssignmentService.handle()` phân tuyến catalog, giao bài, submit, lịch sử và tiến độ; `requireAssignmentVisible()` xác nhận đúng phòng, phạm vi giao và người được xem.

`submit()` xử lý bài nộp cá nhân theo assignment; `createSharedCheck()` tạo snapshot kiểm tra code chung; `createTeacherTrial()` xử lý lượt thử riêng. `SharedRoomCodeProvider` và adapter `GatewaySharedRoomCodeProvider` dùng để lấy snapshot code lõi, tránh tự đọc nhiều trường đang thay đổi ở các thời điểm khác nhau.

Không tạo assignment giả để luyện tập. PRACTICE là luồng độc lập, dùng lại catalog/sandbox nhưng bảng nộp và quy tắc khác.

### 13.2. Kỳ thi

[contests.js](../web-client/static/features/contests/contests.js) có `renderContestCreator()`, `openContest()`, `openContestProblem()`, `submitContestWork()`, `renderLeaderboard()`. `events.js` xử lý submit form, gửi CONTEST_CREATE rồi tải danh sách.

`createContest()` ở service kiểm tra quyền/phòng, tiêu đề/thời gian và danh sách bài theo điều kiện source; contest cá nhân chọn BUILD_FROM_SCRATCH. `submitContest()` kiểm tra kỳ thi, bài, người tham gia, thời hạn và dữ liệu nộp trước khi tạo công việc CONTEST. Worker chấm qua cùng sandbox; repository lưu bài thi và kết quả. Server mới có thẩm quyền kiểm tra thời gian; `contestIsLive()` trên web chỉ giúp hiển thị/khóa nút.

CONTEST_PROBLEMS_GET trả đề và trạng thái acceptedByMe theo tài khoản. LEADERBOARD_GET đọc bảng xếp hạng; frontend dùng `acceptedCount`/các trường tương thích để hiển thị số bài AC. Không tự suy ra bảng điểm dùng thuật toán ICPC hay có penalty nếu không thấy trong source.

**Lỗi form vừa sửa:** chọn checkbox làm render lại form; input title/start/duration trước đó chưa được ghi đủ vào state. Listener input/change nay giữ các trường đó; renderer chọn lại option từ state. Đây là ví dụ vì sao dữ liệu form cần sống ngoài DOM khi ứng dụng render lại toàn vùng giao diện.

## 14. Database transaction và xử lý đồng thời

### 14.1. Hai database mặc định

| Database | Nhóm dữ liệu | Lớp thao tác |
|---|---|---|
| `data/studyfix.db` | users, rooms, revisions | Store |
| Cùng core DB | classroom_sessions, classroom_branches, classroom_checks, classroom_events, classroom_chat | ClassroomRepository qua Store |
| `data/studyfix-learning.db` | p3_exercises, p3_test_cases, assignments, submissions, teacher trials, contests và dữ liệu liên quan | AssignmentRepository |
| Cùng learning DB | p3_practice_submissions | PracticeRepository |

Các model giúp vận chuyển dữ liệu; không phải mỗi model là một dịch vụ chạy riêng. SQLite là file database được JDBC truy cập, không có tiến trình MySQL bắt buộc trong kiến trúc này.

### 14.2. Transaction và thứ tự công bố

`Store.classroomTransaction()` cho `ClassroomRepository.change()` lưu session, branch/event và revision liên quan trong transaction core. Nếu lỗi, rollback thay vì để trạng thái nửa mới/nửa cũ. Sau lưu mới publish vào Room.

`Server.deleteRoom()` cũng ghi database thành công trước rồi mới expire Room. Nó tách các client sau khi ra khỏi khóa Room để tránh đảo thứ tự khóa client/phòng. Không suy ra transaction này bao trùm cả hai database hay bao trùm media peer-to-peer.

### 14.3. Những cơ chế đồng thời khác nhau

| Cơ chế | Bảo vệ/giải quyết gì | Không tự giải quyết gì |
|---|---|---|
| ConcurrentHashMap | Thao tác map kết nối/phòng đồng thời | Một chuỗi quy tắc nghiệp vụ nhiều bước |
| synchronized(room) | Đọc/đổi trạng thái Room trong vùng khóa | Bền vững sau khi tiến trình tắt |
| Transaction SQLite | Nhóm ghi database nhất quán | Tự đồng bộ mọi bản nháp trên trình duyệt |
| expectedVersion/round/phase | Phát hiện request dựa trên trạng thái cũ | Tự hợp nhất nội dung xung đột |
| Queue giới hạn + worker | Kiểm soát công việc chấm và song song | Tài nguyên vô hạn |
| Request counter/generation ở web | Bỏ response cũ/không đúng ngữ cảnh | Thay thế kiểm tra quyền ở backend |

## 15. Bản đồ toàn bộ file production

Các bảng sau bao quát file Java backend và HTML/JS/CSS của giao diện web trong phạm vi source production hiện tại. Mỗi tên file là liên kết tới source; script/test/build được trình bày riêng, không coi artifact build là mã nguồn nghiệp vụ.

### 15.1. Backend lõi và lớp học

| File dưới server/src/studyfix | Nhiệm vụ |
|---|---|
| [Studyfix.java](../server/src/studyfix/Studyfix.java) | Entry point gọi quản lý server |
| [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java) | UI vận hành dịch vụ, thông tin và thao tác quản lý theo source |
| [Server.java](../server/src/studyfix/Server.java) | Listener TCP, map phòng/kết nối, lifecycle và snapshot |
| [ClientHandler.java](../server/src/studyfix/ClientHandler.java) | Xác thực/lệnh cho mỗi socket, gửi qua queue, heartbeat, membership |
| [Client.java](../server/src/studyfix/Client.java) | Console client còn nằm trong server source; không phải web và không phải thư mục Swing đã xóa |
| [Protocol.java](../server/src/studyfix/Protocol.java) | Framing UTF-8, khóa ghi frame, chia lịch sử |
| [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java) | Static web, HTTP API, phiên web, bridge TCP, signaling |
| [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java) | API Java truy cập lõi có kiểm tra danh tính/phòng và snapshot |
| [Store.java](../server/src/studyfix/Store.java) | JDBC core DB, schema, tài khoản, phòng, revision, transaction |
| [Passwords.java](../server/src/studyfix/Passwords.java) | Băm/kiểm tra mật khẩu có salt |
| [Identity.java](../server/src/studyfix/Identity.java) | Dữ liệu tài khoản và role |
| [RoomCode.java](../server/src/studyfix/RoomCode.java) | Chuẩn hóa/xác thực mã phòng và khóa so sánh |
| [Room.java](../server/src/studyfix/Room.java) | Thành viên, code/version, quyền sửa, draft, broadcast, lịch sử |
| [EditSession.java](../server/src/studyfix/EditSession.java) | Mô hình sửa dòng/vùng trên bản gốc |
| [CodeRevision.java](../server/src/studyfix/CodeRevision.java) | Dữ liệu một revision code |
| [CodeDiff.java](../server/src/studyfix/CodeDiff.java) | Tính/biểu diễn khác biệt code |
| [ClassroomService.java](../server/src/studyfix/ClassroomService.java) | State machine phiên, nhánh, Merge và grader nhánh |
| [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java) | Persistence session/branch/check/event/chat |
| [MeetingCalls.java](../server/src/studyfix/MeetingCalls.java) | Roster peer, queue signaling, rate/capacity và cleanup |
| [ScreenShareRelay.java](../server/src/studyfix/screenshare/ScreenShareRelay.java) | Relay native, ticket và frame ảnh, backend vẫn còn khởi tạo |
| [ScreenShareWire.java](../server/src/studyfix/screenshare/ScreenShareWire.java) | Định dạng packet native, không phải WebRTC |

### 15.2. Backend học tập

| File dưới server/src/studyfix/assignment | Nhiệm vụ |
|---|---|
| [AssignmentEnabledServerMain.java](../server/src/studyfix/assignment/AssignmentEnabledServerMain.java) | Entry point console ghép các dịch vụ |
| [AssignmentModuleConfig.java](../server/src/studyfix/assignment/AssignmentModuleConfig.java) | Cấu hình cổng/DB/worker/image |
| [transport/AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java) | Listener JSON TCP và adapter web trực tiếp |
| [protocol/AssignmentWire.java](../server/src/studyfix/assignment/protocol/AssignmentWire.java) | Framing JSON TCP |
| [protocol/AssignmentJson.java](../server/src/studyfix/assignment/protocol/AssignmentJson.java) | JSON codec/helper |
| [service/AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java) | Dispatch type, quyền, submission, queue và worker |
| [persistence/AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java) | Schema/SQL ngân hàng, assignments, thi, kết quả |
| [persistence/PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java) | Lưu riêng luyện tập, version, progress và history |
| [catalog/SeedExerciseCatalog.java](../server/src/studyfix/assignment/catalog/SeedExerciseCatalog.java) | Khai báo ngân hàng đề/test seed và dữ liệu liên quan |
| [grading/GradingSandbox.java](../server/src/studyfix/assignment/grading/GradingSandbox.java) | Interface chấm code |
| [grading/DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java) | Triển khai compile/run/giới hạn/chấm |
| [integration/SharedRoomCodeProvider.java](../server/src/studyfix/assignment/integration/SharedRoomCodeProvider.java) | Hợp đồng lấy code snapshot phòng |
| [integration/GatewaySharedRoomCodeProvider.java](../server/src/studyfix/assignment/integration/GatewaySharedRoomCodeProvider.java) | Adapter lấy snapshot qua gateway lõi |
| [model/Exercise.java](../server/src/studyfix/assignment/model/Exercise.java) | Đề, test và publicView |
| [model/ExerciseMode.java](../server/src/studyfix/assignment/model/ExerciseMode.java) | Phân loại bài lỗi cộng tác/lập trình từ đầu |
| [model/AssignmentRecord.java](../server/src/studyfix/assignment/model/AssignmentRecord.java) | Bài được giao và phạm vi/thời hạn |
| [model/SubmissionRecord.java](../server/src/studyfix/assignment/model/SubmissionRecord.java) | Bài nộp theo assignment |
| [model/ContestSubmissionRecord.java](../server/src/studyfix/assignment/model/ContestSubmissionRecord.java) | Bài nộp trong kỳ thi |
| [model/TeacherTrialRecord.java](../server/src/studyfix/assignment/model/TeacherTrialRecord.java) | Lượt thử của giáo viên |
| [model/SharedCheckRun.java](../server/src/studyfix/assignment/model/SharedCheckRun.java) | Snapshot kiểm tra code chung |
| [model/GradingResult.java](../server/src/studyfix/assignment/model/GradingResult.java) | Verdict, điểm, số test, feedback, thời gian/test results |

### 15.3. Frontend: toàn bộ file dưới web-client/static

| File | Nhiệm vụ |
|---|---|
| [index.html](../web-client/static/index.html) | Root app, toast region, nạp module/CSS |
| [app.js](../web-client/static/app.js) | Ghép module, đăng ký trang và khởi tạo |
| [app.css](../web-client/static/app.css) | Style toàn ứng dụng, form, luyện tập và component |
| [meeting.css](../web-client/static/meeting.css) | Style bố cục cuộc họp/editor/grid |
| [core/state.js](../web-client/static/core/state.js) | State dùng chung, nhớ/khôi phục view |
| [core/api.js](../web-client/static/core/api.js) | Fetch, bearer, JSON, xử lý lỗi HTTP và wrapper API |
| [core/events.js](../web-client/static/core/events.js) | Event delegation click/submit/input/change, form và file import |
| [core/polling.js](../web-client/static/core/polling.js) | Tải dữ liệu trang, poll kết quả/tín hiệu/sự kiện, điều hướng |
| [core/lifecycle.js](../web-client/static/core/lifecycle.js) | Khởi tạo lại phiên khi tải trang |
| [core/router.js](../web-client/static/core/router.js) | Registry tên trang → renderer |
| [core/dom.js](../web-client/static/core/dom.js) | Root DOM, gắn/bảo toàn stream và media element |
| [features/auth/auth.js](../web-client/static/features/auth/auth.js) | Login/logout/refresh danh tính và dọn trạng thái |
| [features/room/room.js](../web-client/static/features/room/room.js) | Tạo/vào/rời phòng, rời họp, lịch sử, code chung và công cụ liên quan |
| [features/classroom/classroom.js](../web-client/static/features/classroom/classroom.js) | Nháp Master/nhánh, lưu/check/action/Merge và báo cáo |
| [features/classroom/meeting-view.js](../web-client/static/features/classroom/meeting-view.js) | Render giao diện lớp học online |
| [features/classroom/call.js](../web-client/static/features/classroom/call.js) | Mic/camera, RTCPeerConnection, signaling/cleanup |
| [features/classroom/chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js) | Chat polling, mốc đọc, badge và popup |
| [features/screen-share/screen-share.js](../web-client/static/features/screen-share/screen-share.js) | Capture/share/receive màn hình và audio |
| [features/exercises/practice.js](../web-client/static/features/exercises/practice.js) | Luyện tập độc lập, nháp, pending, version/history |
| [features/exercises/exercises.js](../web-client/static/features/exercises/exercises.js) | Assignment phòng, tiến độ, trial, kết quả dùng chung |
| [features/contests/contests.js](../web-client/static/features/contests/contests.js) | Form kỳ thi, đề thi, workspace/nộp và BXH |
| [ui/render.js](../web-client/static/ui/render.js) | Layout, overview/directory, render vùng app và giữ focus/media |
| [ui/components.js](../web-client/static/ui/components.js) | Nút, pill, empty state dùng chung |
| [ui/modal.js](../web-client/static/ui/modal.js) | Mở/đóng modal |
| [ui/toast.js](../web-client/static/ui/toast.js) | Thông báo, lỗi, popup chat và busy UI |
| [shared/format.js](../web-client/static/shared/format.js) | Escape HTML, ngày giờ, tên/màu verdict/status |
| [shared/crypto.js](../web-client/static/shared/crypto.js) | ID ngẫu nhiên phục vụ request theo cách frontend triển khai |

## 16. Kiểm thử giới hạn và cách debug

### 16.1. Hạ tầng build và kiểm thử

[build.xml](../server/build.xml) định nghĩa target build/test Ant; `nbproject/` chứa cấu hình build NetBeans. `server/lib/` có JDBC dependency; `server/test/` chứa test backend; `web-client/tests/` chứa script Node kiểm tra frontend. Các thư mục build/dist chứa đầu ra, không phải nơi đọc logic nghiệp vụ chính.

Các nhóm kiểm thử có tên như RoomLifecycle, Classroom, WebGateway, MeetingCalls, Practice và script `check_classroom.mjs`, `check_practice.mjs`, `check_meeting.mjs`, `check_chat_notifications.mjs`, `check_room_directory.mjs`. `check_contest_form.mjs` kiểm tra hồi quy giữ dữ liệu form tạo kỳ thi. Đọc cấu hình target và từng test trước khi suy ra chúng là E2E thật.

**Lưu ý sau khi bỏ Swing:** `check_seven_bugfixes.mjs` còn đọc file Swing đã xóa, nên bài test đó cần được cập nhật nếu tiếp tục dùng suite này. Tài liệu phân tích không tự sửa test hoặc khôi phục client đã xóa.

### 16.2. Các giới hạn quan sát được trong source

- WebRTC chưa có SFU/TURN riêng; call giới hạn 16 thiết bị. Không gọi hệ thống là bản sao kiến trúc Zoom.
- Merge chọn toàn bộ một nhánh; không có Git merge tự động và không bắt buộc nhánh AC trước khi Merge.
- Một số buffer/queue có trần; polling không phải event log vô hạn hay WebSocket.
- `practice.scope()` reset nhiều trường khi đổi user nhưng chưa reset Map `drafts`/`requests`; `draftFor()` ưu tiên Map keyed theo bài. Cần kiểm tra trường hợp đổi tài khoản rồi mở cùng bài để tránh nháp RAM của người trước. Đây là nhận xét phân tích, chưa sửa trong tác vụ này.
- Tài liệu hướng dẫn cũ có những phần mô tả client Swing lịch sử; tài liệu này là bản phân tích theo phạm vi web/server sau khi xóa thư mục client.
- Không có số đo tải, tỷ lệ AC thực tế, thời gian phản hồi trung bình hay kết quả thử LAN mới trong tác vụ viết tài liệu này.

### 16.3. Lần theo lỗi bằng một thao tác cụ thể

| Hiện tượng | Điểm kiểm tra theo thứ tự |
|---|---|
| Bấm nút không có request | `events.js` → action/selector → hàm feature |
| Request 401 | token trong `api()` → `requireSession()` → WebSession/socket lõi |
| Vào phòng bị từ chối | membership hiện tại → RoomCode → handler/Server.findRoom |
| Đổi phase/Merge lỗi | action/round/expectedPhase → masterVersion/branchVersion → chủ phòng |
| Nhánh chưa lưu | debounce → `save()`/dirty/baseVersion → BRANCH_SAVE → repository |
| Bài chấm đứng chờ | submissionId/status → queue/worker → Docker → `finish()` → polling |
| Thấy camera mình nhưng người khác không thấy | capture track → JOIN/roster → offer/answer/ICE → sender → DOM phía nhận |
| Share có hình không có tiếng | audio track đầu vào → sender → remote track → autoplay/audio element |
| Tên kỳ thi biến mất khi chọn bài | input state → checkbox render → value/selected của form |
| Sửa Java mà hành vi không đổi | source → bản build → tiến trình đang chạy; refresh web không thay JVM cũ |

### 16.4. Ba bài tự lần source

**Bài A — học sinh lưu nhánh:** tìm `classroomInput()` → `save("branch")` → `sendAction()` → `/api/classroom` → `ClassroomService.action()` case BRANCH_SAVE → `saveBranch()` → view mới. Giải thích lý do expectedVersion có thể bị từ chối.

**Bài B — giáo viên Merge:** tìm handler `classroom-preview` → `branchDetail()` → `classroom-merge` → case MERGE → `revision()` → `ClassroomRepository.change()` → `publishClassroomRevision()`. Chỉ ra thời điểm ghi DB và thời điểm thay đổi RAM.

**Bài C — chạy thử và nộp luyện tập:** tìm `submitPractice(run)` → `assignment()` → `assignmentRequest()` → `handlePractice()` → `createPractice()` → `PracticeRepository.create()` → `workLoop()` → `DockerJavaSandbox.grade()` → `finish()` → `pollPractice()`. Chỉ ra chỗ chọn public-only test khi run và chỗ giữ source bất biến của bài nộp.

## 17. Chỉ mục hàm và số dòng tự tra trong IDE

Chỉ mục dưới đây được lấy từ khai báo trong file source ở thời điểm viết. Nó giúp mở đúng vùng code; số dòng có thể đổi khi sửa chương trình. Các helper ngắn được giải thích theo nhóm ở những mục trên, không phải mọi hàm đều là một request endpoint.

Kiểm kê hiện tại bao quát **69 file production**. Chỉ mục bên dưới trích tên hàm và số dòng từ khai báo trong source; `line` nghĩa là dòng. Đây là công cụ tra cứu, không phải parser Java/JavaScript hoàn chỉnh: constructor, lambda, khai báo nhiều hàm cùng dòng hoặc một số cách viết đặc biệt có thể không xuất hiện. Khi cần, mở file và Ctrl+F tên hàm trong phần phân tích phía trên.

### server/src/studyfix/assignment/AssignmentEnabledServerMain.java

Source: [AssignmentEnabledServerMain.java](../server/src/studyfix/assignment/AssignmentEnabledServerMain.java).

`AssignmentEnabledServerMain()` (line 10); `main()` (line 11).

### server/src/studyfix/assignment/AssignmentModuleConfig.java

Source: [AssignmentModuleConfig.java](../server/src/studyfix/assignment/AssignmentModuleConfig.java).

`load()` (line 4).

### server/src/studyfix/assignment/catalog/SeedExerciseCatalog.java

Source: [SeedExerciseCatalog.java](../server/src/studyfix/assignment/catalog/SeedExerciseCatalog.java).

`SeedExerciseCatalog()` (line 9); `t()` (line 10); `all()` (line 12); `debug()` (line 115).

### server/src/studyfix/assignment/grading/DockerJavaSandbox.java

Source: [DockerJavaSandbox.java](../server/src/studyfix/assignment/grading/DockerJavaSandbox.java).

`DockerJavaSandbox()` (line 30); `grade()` (line 38); `dockerBase()` (line 170); `runProcess()` (line 182); `drain()` (line 245); `killContainer()` (line 258); `testRow()` (line 266); `unavailable()` (line 276); `result()` (line 280); `normalize()` (line 286); `parseInt()` (line 292); `remainingMillis()` (line 296); `elapsed()` (line 300); `containerName()` (line 304); `safe()` (line 308); `failed()` (line 316); `BoundedCapture()` (line 326); `add()` (line 327); `exceeded()` (line 333); `text()` (line 334).

### server/src/studyfix/assignment/integration/GatewaySharedRoomCodeProvider.java

Source: [GatewaySharedRoomCodeProvider.java](../server/src/studyfix/assignment/integration/GatewaySharedRoomCodeProvider.java).

`GatewaySharedRoomCodeProvider()` (line 10); `snapshot()` (line 11); `loadStarterCode()` (line 15).

### server/src/studyfix/assignment/model/AssignmentRecord.java

Source: [AssignmentRecord.java](../server/src/studyfix/assignment/model/AssignmentRecord.java).

`AssignmentRecord()` (line 6).

### server/src/studyfix/assignment/model/Exercise.java

Source: [Exercise.java](../server/src/studyfix/assignment/model/Exercise.java).

`Exercise()` (line 17); `publicView()` (line 29).

### server/src/studyfix/assignment/model/ExerciseMode.java

Source: [ExerciseMode.java](../server/src/studyfix/assignment/model/ExerciseMode.java).

`parse()` (line 8).

### server/src/studyfix/assignment/persistence/AssignmentRepository.java

Source: [AssignmentRepository.java](../server/src/studyfix/assignment/persistence/AssignmentRepository.java).

`AssignmentRepository()` (line 22); `open()` (line 29); `createSchema()` (line 38); `migrateContestProblems()` (line 91); `ensureColumn()` (line 116); `seed()` (line 126); `listExercises()` (line 161); `listExercises()` (line 165); `getExercise()` (line 179); `latestRoomDebugStarterCode()` (line 197); `createAssignment()` (line 211); `createAssignment()` (line 214); `hasAssignmentRecipientSnapshot()` (line 243); `listAssignmentRecipients()` (line 248); `assignment()` (line 253); `getAssignment()` (line 254); `listAssignments()` (line 255); `byIdempotency()` (line 257); `createSubmission()` (line 258); `createSubmission()` (line 259); `submission()` (line 271); `getSubmission()` (line 272); `listSubmissions()` (line 273); `hasAcceptedSubmission()` (line 275); `hasAcceptedContestSubmission()` (line 283); `markRunning()` (line 290); `recoverQueuedSubmissions()` (line 291); `updateStatus()` (line 297); `saveResult()` (line 298); `publicTestResults()` (line 309); `teacherTrialByIdempotency()` (line 310); `getTeacherTrial()` (line 315); `listTeacherTrials()` (line 320); `createTeacherTrial()` (line 327); `markTeacherTrialRunning()` (line 333); `recoverQueuedTeacherTrials()` (line 336); `saveTeacherTrialResult()` (line 340); `teacherTrialTestResults()` (line 355); `teacherTrial()` (line 360); `sharedCheckByRequestKey()` (line 364); `getSharedCheck()` (line 369); `latestSharedCheck()` (line 374); `createSharedCheck()` (line 379); `recoverQueuedSharedChecks()` (line 383); `markSharedCheckRunning()` (line 387); `saveSharedCheckResult()` (line 390); `sharedCheck()` (line 393); `createContest()` (line 398); `createContestFromExercises()` (line 413); `createContestRows()` (line 426); `listContests()` (line 452); `contest()` (line 461); `contestAssignments()` (line 469); `contestExerciseIds()` (line 474); `contestProblemId()` (line 479); `contestHasExercise()` (line 484); `contestSubmissionByIdempotency()` (line 486); `getContestSubmission()` (line 491); `contestSubmission()` (line 496); `createContestSubmission()` (line 502); `recoverQueuedContestSubmissions()` (line 512); `markContestSubmissionRunning()` (line 518); `saveContestSubmissionResult()` (line 521); `contestSubmissionTestResults()` (line 536); `listContestSubmissions()` (line 542); `leaderboard()` (line 550); `legacyLeaderboard()` (line 583).

### server/src/studyfix/assignment/persistence/PracticeRepository.java

Source: [PracticeRepository.java](../server/src/studyfix/assignment/persistence/PracticeRepository.java).

`PracticeRepository()` (line 13); `open()` (line 21); `row()` (line 25); `get()` (line 28); `byKey()` (line 31); `create()` (line 34); `running()` (line 48); `finish()` (line 49); `recover()` (line 60); `progress()` (line 66); `history()` (line 71).

### server/src/studyfix/assignment/protocol/AssignmentJson.java

Source: [AssignmentJson.java](../server/src/studyfix/assignment/protocol/AssignmentJson.java).

`AssignmentJson()` (line 7); `stringify()` (line 8); `write()` (line 9); `quote()` (line 21); `parse()` (line 24); `object()` (line 25); `ws()` (line 28); `value()` (line 29); `take()` (line 30); `object()` (line 31); `array()` (line 32); `string()` (line 33); `number()` (line 34); `err()` (line 35); `text()` (line 37); `number()` (line 38); `integer()` (line 39); `map()` (line 40); `list()` (line 41); `mapOf()` (line 42).

### server/src/studyfix/assignment/protocol/AssignmentWire.java

Source: [AssignmentWire.java](../server/src/studyfix/assignment/protocol/AssignmentWire.java).

`AssignmentWire()` (line 6); `read()` (line 7); `write()` (line 8).

### server/src/studyfix/assignment/service/AssignmentService.java

Source: [AssignmentService.java](../server/src/studyfix/assignment/service/AssignmentService.java).

`AssignmentService()` (line 27); `handle()` (line 37); `handlePractice()` (line 139); `practiceSearch()` (line 176); `practiceView()` (line 177); `createPractice()` (line 178); `createTeacherTrial()` (line 194); `teacherTrialMap()` (line 214); `createContest()` (line 216); `submitContest()` (line 234); `contestSubmissionMap()` (line 255); `submit()` (line 256); `createSharedCheck()` (line 308); `metrics()` (line 329); `workLoop()` (line 330); `currentRoomStudents()` (line 383); `classProgress()` (line 384); `isAccepted()` (line 407); `findRoomStudent()` (line 408); `requireAssignmentVisible()` (line 409); `requireOwner()` (line 410); `assignmentMap()` (line 411); `requireDebug()` (line 412); `sha256()` (line 413); `checkState()` (line 414); `sharedCheckMap()` (line 415); `submissionMap()` (line 416); `ok()` (line 417); `error()` (line 418); `safe()` (line 419); `close()` (line 420); `classroomExercise()` (line 422).

### server/src/studyfix/assignment/transport/AssignmentTcpServer.java

Source: [AssignmentTcpServer.java](../server/src/studyfix/assignment/transport/AssignmentTcpServer.java).

`AssignmentTcpServer()` (line 17); `port()` (line 18); `handleWebRequest()` (line 20); `isRunning()` (line 21); `metrics()` (line 22); `classroomExercise()` (line 23); `start()` (line 24); `acceptLoop()` (line 25); `serve()` (line 26); `close()` (line 27).

### server/src/studyfix/ClassroomRepository.java

Source: [ClassroomRepository.java](../server/src/studyfix/ClassroomRepository.java).

`ClassroomRepository()` (line 15); `migrate()` (line 17); `session()` (line 34); `saveSession()` (line 45); `change()` (line 50); `insertBranch()` (line 61); `branches()` (line 66); `saveBranch()` (line 76); `checks()` (line 85); `createCheck()` (line 95); `check()` (line 102); `result()` (line 112); `report()` (line 119); `appendChat()` (line 128); `chat()` (line 135); `readChat()` (line 138); `chatStatus()` (line 145).

### server/src/studyfix/ClassroomService.java

Source: [ClassroomService.java](../server/src/studyfix/ClassroomService.java).

`ClassroomService()` (line 26); `view()` (line 30); `action()` (line 63); `checkDetail()` (line 156); `branchDetail()` (line 165); `roundReport()` (line 175); `historical()` (line 186); `requireExercise()` (line 191); `member()` (line 196); `phase()` (line 199); `sameRound()` (line 200); `idle()` (line 201); `masterVersion()` (line 202); `source()` (line 207); `branch()` (line 212); `revision()` (line 213); `checkMap()` (line 217); `grade()` (line 222); `close()` (line 233).

### server/src/studyfix/Client.java

Source: [Client.java](../server/src/studyfix/Client.java).

`ConsoleOutput()` (line 22); `println()` (line 24); `historyPart()` (line 29); `finish()` (line 53); `flushPending()` (line 62); `main()` (line 67).

### server/src/studyfix/ClientHandler.java

Source: [ClientHandler.java](../server/src/studyfix/ClientHandler.java).

`ClientHandler()` (line 29); `getMemberId()` (line 38); `identity()` (line 39); `getCurrentRoomId()` (line 41); `isConnected()` (line 42); `getDisplayName()` (line 43); `send()` (line 44); `sendLong()` (line 49); `enqueue()` (line 53); `writeLoop()` (line 62); `closeConnection()` (line 74); `requireOutsideRoom()` (line 81); `leaveRoom()` (line 89); `leaveCurrentRoom()` (line 98); `leaveDeletedRoom()` (line 103); `createAndJoinRoom()` (line 111); `joinExistingRoom()` (line 118); `handleDraftCommand()` (line 127); `run()` (line 168).

### server/src/studyfix/CodeDiff.java

Source: [CodeDiff.java](../server/src/studyfix/CodeDiff.java).

`CodeDiff()` (line 10); `display()` (line 13); `appendChanges()` (line 45); `lines()` (line 78); `visible()` (line 91); `row()` (line 99); `sparse()` (line 103); `between()` (line 141).

### server/src/studyfix/CodeRevision.java

Source: [CodeRevision.java](../server/src/studyfix/CodeRevision.java).

`CodeRevision()` (line 21); `CodeRevision()` (line 26); `CodeRevision()` (line 32); `getVersion()` (line 43); `getEditorId()` (line 44); `getEditorName()` (line 45); `getCreatedAt()` (line 46); `getSourceCode()` (line 47); `getPreviousVersion()` (line 49); `getChanges()` (line 50); `detail()` (line 51); `summary()` (line 61).

### server/src/studyfix/EditSession.java

Source: [EditSession.java](../server/src/studyfix/EditSession.java).

`EditSession()` (line 18); `version()` (line 42); `isRange()` (line 43); `checkLine()` (line 44); `endingOf()` (line 47); `content()` (line 50); `limit()` (line 51); `setLine()` (line 55); `deleteLine()` (line 56); `keepLine()` (line 57); `changeLine()` (line 58); `setRange()` (line 66); `deleteRange()` (line 67); `keepRange()` (line 68); `changeRange()` (line 69); `complete()` (line 75); `replacement()` (line 78); `build()` (line 84); `status()` (line 97); `changes()` (line 112); `append()` (line 127).

### server/src/studyfix/MeetingCalls.java

Source: [MeetingCalls.java](../server/src/studyfix/MeetingCalls.java).

`join()` (line 18); `state()` (line 27); `signal()` (line 30); `poll()` (line 42); `member()` (line 53); `leave()` (line 58); `prune()` (line 63).

### server/src/studyfix/Passwords.java

Source: [Passwords.java](../server/src/studyfix/Passwords.java).

`hash()` (line 11); `verify()` (line 16); `derive()` (line 21); `Passwords()` (line 27).

### server/src/studyfix/Protocol.java

Source: [Protocol.java](../server/src/studyfix/Protocol.java).

`Protocol()` (line 9); `historyFrames()` (line 12); `receive()` (line 32); `send()` (line 42).

### server/src/studyfix/Room.java

Source: [Room.java](../server/src/studyfix/Room.java).

`Room()` (line 35); `getRoomId()` (line 45); `getRoomCode()` (line 46); `isExpired()` (line 47); `expire()` (line 48); `isMember()` (line 49); `isOwner()` (line 50); `canEdit()` (line 53); `legacyEditAllowed()` (line 57); `classroomPhase()` (line 60); `publishClassroomRevision()` (line 61); `classroomNotice()` (line 66); `snapshot()` (line 73); `memberDetails()` (line 82); `hasActiveDraft()` (line 96); `recoverableDraftFor()` (line 98); `acknowledgeRecoverableDraft()` (line 102); `hasActiveEditTurn()` (line 107); `loadAssignmentStarter()` (line 114); `addMember()` (line 152); `removeMember()` (line 159); `getMembers()` (line 170); `nameOf()` (line 177); `state()` (line 181); `showCode()` (line 188); `showNumberedCode()` (line 192); `requestEdit()` (line 202); `requestEditPriority()` (line 212); `nextEditor()` (line 241); `releaseEdit()` (line 250); `updateCode()` (line 258); `selectLines()` (line 287); `validEditor()` (line 297); `validDraft()` (line 303); `editDraft()` (line 309); `showDraft()` (line 325); `cancelDraft()` (line 328); `finishDraft()` (line 333); `showHistory()` (line 355); `showRevision()` (line 367); `broadcast()` (line 384); `chat()` (line 393).

### server/src/studyfix/RoomCode.java

Source: [RoomCode.java](../server/src/studyfix/RoomCode.java).

`normalize()` (line 8); `key()` (line 14).

### server/src/studyfix/screenshare/ScreenShareRelay.java

Source: [ScreenShareRelay.java](../server/src/studyfix/screenshare/ScreenShareRelay.java).

`ScreenShareRelay()` (line 49); `port()` (line 56); `issueTicket()` (line 59); `acceptLoop()` (line 69); `handleSocket()` (line 80); `publishControlEvent()` (line 128); `onPeerClosed()` (line 132); `close()` (line 147); `Peer()` (line 163); `send()` (line 166); `acceptFrameTime()` (line 167); `offerFrame()` (line 172); `startViewerSender()` (line 176); `closeSocket()` (line 186).

### server/src/studyfix/screenshare/ScreenShareWire.java

Source: [ScreenShareWire.java](../server/src/studyfix/screenshare/ScreenShareWire.java).

`Type()` (line 16); `from()` (line 17); `ScreenShareWire()` (line 23); `read()` (line 24); `write()` (line 31); `utf8()` (line 37); `fromUtf8()` (line 38).

### server/src/studyfix/Server.java

Source: [Server.java](../server/src/studyfix/Server.java).

`Server()` (line 27); `start()` (line 56); `acceptLoop()` (line 62); `executeWriter()` (line 76); `store()` (line 77); `createRoom()` (line 78); `createRoom()` (line 81); `findRoom()` (line 92); `deleteRoom()` (line 98); `port()` (line 118); `screenSharePort()` (line 119); `isRunning()` (line 120); `log()` (line 121); `snapshot()` (line 130); `gateway()` (line 136); `screenShare()` (line 138); `addDepartureListener()` (line 139); `removeDepartureListener()` (line 140); `departed()` (line 141); `close()` (line 148); `main()` (line 163).

### server/src/studyfix/ServerAdmin.java

Source: [ServerAdmin.java](../server/src/studyfix/ServerAdmin.java).

`isCellEditable()` (line 21); `ServerAdmin()` (line 42); `windowClosing()` (line 76); `startServices()` (line 90); `selectedRoomId()` (line 163); `selectedRoom()` (line 168); `render()` (line 174); `render()` (line 183); `renderSnapshot()` (line 208); `showHistory()` (line 223); `showRevision()` (line 240); `shutdown()` (line 249); `main()` (line 286).

### server/src/studyfix/Store.java

Source: [Store.java](../server/src/studyfix/Store.java).

`Store()` (line 18); `openConnection()` (line 39); `transaction()` (line 53); `restoreAutoCommit()` (line 74); `replaceConnection()` (line 81); `requireAvailable()` (line 92); `migrateHistory()` (line 96); `seed()` (line 128); `migrateRoomCodes()` (line 138); `login()` (line 151); `loadRooms()` (line 163); `nullableLong()` (line 174); `loadHistory()` (line 178); `createRoom()` (line 188); `createRoom()` (line 191); `expireRoom()` (line 214); `saveRevision()` (line 217); `persistRevision()` (line 224); `classroomTransaction()` (line 232); `classroomPhase()` (line 235); `insertRevision()` (line 242); `close()` (line 250).

### server/src/studyfix/Studyfix.java

Source: [Studyfix.java](../server/src/studyfix/Studyfix.java).

`main()` (line 16).

### server/src/studyfix/StudyFixGateway.java

Source: [StudyFixGateway.java](../server/src/studyfix/StudyFixGateway.java).

`StudyFixGateway()` (line 12); `identity()` (line 13); `session()` (line 14); `memberRoom()` (line 19); `isMember()` (line 25); `isTeacher()` (line 29); `codeSnapshot()` (line 33); `assignmentCodeSnapshot()` (line 44); `loadAssignmentStarterCode()` (line 55); `canEdit()` (line 66); `sendToUser()` (line 67); `sendToRoom()` (line 74); `addDepartureListener()` (line 81); `removeDepartureListener()` (line 82).

### server/src/studyfix/WebGatewayServer.java

Source: [WebGatewayServer.java](../server/src/studyfix/WebGatewayServer.java).

`WebGatewayServer()` (line 63); `WebGatewayServer()` (line 66); `envOrDefault()` (line 85); `envPort()` (line 90); `createHttpServer()` (line 102); `start()` (line 123); `port()` (line 131); `scheme()` (line 132); `isRunning()` (line 133); `handle()` (line 135); `handleApi()` (line 164); `login()` (line 221); `allowLoginAttempt()` (line 240); `requireSession()` (line 249); `rooms()` (line 257); `currentRoomId()` (line 278); `requireCurrentRoom()` (line 285); `currentRoom()` (line 293); `roomHistory()` (line 322); `roomCommand()` (line 331); `events()` (line 414); `assignmentRequest()` (line 421); `screenSignal()` (line 435); `registerScreenShare()` (line 503); `endScreenShare()` (line 516); `activePresenterForRoom()` (line 523); `sessionIsInRoom()` (line 535); `broadcastScreenSignal()` (line 541); `pollScreenSignals()` (line 548); `pruneCalls()` (line 559); `callSignal()` (line 562); `serveStatic()` (line 579); `contentType()` (line 602); `readBody()` (line 614); `roomSearchKey()` (line 626); `queryText()` (line 630); `queryLong()` (line 638); `positiveLong()` (line 643); `nonNegativeLong()` (line 644); `text()` (line 645); `map()` (line 646); `identityMap()` (line 647); `safeMessage()` (line 648); `securityHeaders()` (line 650); `corsPreflight()` (line 655); `json()` (line 664); `cleanup()` (line 671); `closeSession()` (line 676); `close()` (line 687); `asMap()` (line 696); `touch()` (line 708); `WebSession()` (line 709); `login()` (line 712); `send()` (line 727); `readLoop()` (line 728); `addEvent()` (line 733); `eventsAfter()` (line 736); `addScreenSignal()` (line 737); `screenSignalsAfter()` (line 738); `markClosed()` (line 739); `close()` (line 740); `ApiException()` (line 744).

### web-client/static/core/api.js

Source: [api.js](../web-client/static/core/api.js).

`api()` (line 4); `assignment()` (line 43); `command()` (line 53).

### web-client/static/core/dom.js

Source: [dom.js](../web-client/static/core/dom.js).

`bindVideoElements()` (line 8); `captureMediaElements()` (line 37); `restoreMediaElements()` (line 40).

### web-client/static/core/events.js

Source: [events.js](../web-client/static/core/events.js).

`queueRoomSearch()` (line 565).

### web-client/static/core/lifecycle.js

Source: [lifecycle.js](../web-client/static/core/lifecycle.js).

`init()` (line 7).

### web-client/static/core/polling.js

Source: [polling.js](../web-client/static/core/polling.js).

`setPracticeHandlers()` (line 18); `setRoomJoinedHandler()` (line 19); `setScreenSignalHandler()` (line 24); `invalidateRoomRequests()` (line 30); `loadCurrentRoom()` (line 31); `handleDeletedRoom()` (line 66); `loadRooms()` (line 75); `loadAssignments()` (line 90); `loadCatalog()` (line 105); `loadStudents()` (line 120); `loadProgress()` (line 130); `loadContests()` (line 141); `loadPageData()` (line 152); `startPolling()` (line 187); `stopPolling()` (line 239); `receiveEvent()` (line 244); `cleanSystem()` (line 281); `refreshEverything()` (line 286); `navigate()` (line 293); `pollSubmissionResult()` (line 305).

### web-client/static/core/router.js

Source: [router.js](../web-client/static/core/router.js).

`registerPage()` (line 12); `renderPage()` (line 16).

### web-client/static/core/state.js

Source: [state.js](../web-client/static/core/state.js).

`rememberView()` (line 94); `restoreView()` (line 98).

### web-client/static/features/auth/auth.js

Source: [auth.js](../web-client/static/features/auth/auth.js).

`login()` (line 23); `logout()` (line 46); `refreshMe()` (line 78).

### web-client/static/features/classroom/call.js

Source: [call.js](../web-client/static/features/classroom/call.js).

`signal()` (line 14); `refresh()` (line 18); `localFlags()` (line 19); `capture()` (line 22); `joinCall()` (line 26); `watchTracks()` (line 47); `makePeer()` (line 52); `bindSenders()` (line 68); `dropPeer()` (line 84); `leaveCall()` (line 88); `toggleDevice()` (line 98); `pollCall()` (line 119); `pollCallOnce()` (line 125); `callClick()` (line 173).

### web-client/static/features/classroom/chat-notifications.js

Source: [chat-notifications.js](../web-client/static/features/classroom/chat-notifications.js).

`scope()` (line 7); `record()` (line 8); `persist()` (line 16); `unreadChatCount()` (line 19); `updateChatBadges()` (line 20); `markChatReadIfVisible()` (line 28); `loadChat()` (line 38); `isOwn()` (line 68); `resetChatNotifications()` (line 71).

### web-client/static/features/classroom/classroom.js

Source: [classroom.js](../web-client/static/features/classroom/classroom.js).

`draftKey()` (line 16); `draft()` (line 20); `persistDraft()` (line 32); `myBranch()` (line 39); `verdict()` (line 40); `resultPanel()` (line 46); `historyChecks()` (line 51); `dialog()` (line 54); `localRecovery()` (line 57); `loadClassroom()` (line 69); `scheduleAutoCheck()` (line 82); `renderClassroom()` (line 95); `sendAction()` (line 116); `save()` (line 129); `runCheck()` (line 146); `classroomInput()` (line 151); `classroomClick()` (line 172).

### web-client/static/features/classroom/meeting-view.js

Source: [meeting-view.js](../web-client/static/features/classroom/meeting-view.js).

`icon()` (line 7); `tool()` (line 11); `status()` (line 15); `highlight()` (line 24); `videoTile()` (line 30); `editor()` (line 35); `screenPanel()` (line 39); `renderMeeting()` (line 44).

### web-client/static/features/contests/contests.js

Source: [contests.js](../web-client/static/features/contests/contests.js).

`renderContests()` (line 12); `contestIsLive()` (line 24); `renderContestCard()` (line 29); `renderContestWorkspace()` (line 37); `renderContestCreator()` (line 45); `renderContestDetail()` (line 72); `renderLeaderboard()` (line 80); `openContest()` (line 84); `openContestProblem()` (line 104); `submitContestWork()` (line 127).

### web-client/static/features/exercises/exercises.js

Source: [exercises.js](../web-client/static/features/exercises/exercises.js).

`renderExercises()` (line 12); `renderExerciseDetail()` (line 33); `renderAssignmentForm()` (line 37); `renderProgress()` (line 42); `renderStudentAssignments()` (line 50); `renderStudentAssignmentRows()` (line 66); `renderPersonalWorkspace()` (line 76); `renderSubmissionResult()` (line 84); `renderSubmissions()` (line 88); `openAssignment()` (line 100); `openTeacherTrial()` (line 139); `submitWork()` (line 174); `teacherTrialSubmit()` (line 196); `reloadSubmissions()` (line 215); `openSubmission()` (line 257); `renderProgressDetail()` (line 269); `renderStudentProgressDetail()` (line 273).

### web-client/static/features/exercises/practice.js

Source: [practice.js](../web-client/static/features/exercises/practice.js).

`read()` (line 16); `write()` (line 17); `draftFor()` (line 18); `selection()` (line 19); `savedSelection()` (line 20); `scope()` (line 21); `savePracticeDraft()` (line 27); `practiceBadge()` (line 30); `loadPracticeList()` (line 31); `loadPractice()` (line 41); `refreshExerciseStatus()` (line 46); `loadPracticeHistory()` (line 51); `openPractice()` (line 59); `submitPractice()` (line 74); `pollPractice()` (line 93); `practiceClick()` (line 108); `practiceInput()` (line 126); `options()` (line 131); `pagination()` (line 132); `renderPractice()` (line 133); `renderPracticeWork()` (line 139).

### web-client/static/features/room/room.js

Source: [room.js](../web-client/static/features/room/room.js).

`renderRoom()` (line 16); `leaveMeeting()` (line 21); `leaveRoomMembership()` (line 29); `renderCollabWorkspace()` (line 49); `membersCountText()` (line 91); `renderDebugStatement()` (line 95); `renderRecoveryDraft()` (line 108); `renderSharedCheck()` (line 114); `renderChat()` (line 118); `renderMembers()` (line 123); `fetchDebugExercise()` (line 127); `loadHistory()` (line 138); `runSharedCheck()` (line 145); `commandCreateRoom()` (line 185); `joinRoom()` (line 201); `openStarterLoadConfirm()` (line 222); `replaceStarterCode()` (line 231); `loadHistoryAndFind()` (line 258); `renderStatementModal()` (line 264).

### web-client/static/features/screen-share/screen-share.js

Source: [screen-share.js](../web-client/static/features/screen-share/screen-share.js).

`startScreenShare()` (line 11); `offerScreenTo()` (line 68); `sendSignal()` (line 89); `setRemotePresenter()` (line 96); `flushPendingIce()` (line 102); `showRemoteStream()` (line 111); `playScreenAudio()` (line 120); `receiveScreenSignal()` (line 128); `clearRemotePresenter()` (line 222); `stopScreenShare()` (line 240); `renderScreenWorkspace()` (line 260); `escapeText()` (line 275).

### web-client/static/shared/crypto.js

Source: [crypto.js](../web-client/static/shared/crypto.js).

`cryptoRandomId()` (line 2); `sha256()` (line 9).

### web-client/static/shared/format.js

Source: [format.js](../web-client/static/shared/format.js).

`nowMs()` (line 111).

### web-client/static/ui/modal.js

Source: [modal.js](../web-client/static/ui/modal.js).

`modal()` (line 2); `clearModal()` (line 12).

### web-client/static/ui/render.js

Source: [render.js](../web-client/static/ui/render.js).

`layout()` (line 9); `pageHeader()` (line 29); `renderLogin()` (line 33); `renderOverview()` (line 38); `renderRoomListCompact()` (line 49); `renderAssignmentRows()` (line 63); `renderModal()` (line 73); `render()` (line 77).

### web-client/static/ui/toast.js

Source: [toast.js](../web-client/static/ui/toast.js).

`toast()` (line 5); `notifyChatMessage()` (line 14); `fail()` (line 24); `busy()` (line 28).
