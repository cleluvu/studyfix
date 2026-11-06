# Kế hoạch đầy đủ — Thành viên 2: Client Swing và chia sẻ màn hình

## 1. Định nghĩa trách nhiệm

Thành viên 2 chịu trách nhiệm tạo client Java Swing từ đầu, kết nối đúng protocol server do thành viên 1 bàn giao, thể hiện đúng trạng thái phòng/code/quyền sửa trong GUI, và cung cấp module chia sẻ màn hình ở cả hai đầu client/server. Phần bài tập, nộp bài, bộ chấm, thi và xếp hạng thuộc thành viên 3; thành viên 2 chỉ cung cấp khung UI, connection context và điểm ghép để tích hợp module đó.

**Không** tạo server giả trong client. Client không tự đặt quyền editor, tự cập nhật version, tự chấm điểm hoặc báo nộp bài thành công. Mọi thay đổi code và quyền đều chờ thông điệp server.

## 2. Kiến trúc đề xuất

```text
StudyFixApplication
└── LoginFrame
    └── AuthService -> TcpControlConnection (1 socket điều khiển tồn tại lâu dài)
        └── MainFrame
            ├── RoomControlsPanel
            ├── SharedCodePanel -> CodeCollaborationService -> /code, /request, /select ...
            ├── ChatPanel -> RoomService -> /chat
            ├── MembersPanel -> RoomService -> /members, /kick
            ├── HistoryPanel -> CodeCollaborationService -> /history
            ├── ScreenSharePanel -> /screen-ticket -> socket màn hình riêng (port 5001, sau khi server patch)
            ├── ExerciseIntegrationPanel (placeholder cho module thành viên 3)
            └── ActivityPanel (log phản hồi server để debug)
```

Networking không được gọi trực tiếp từ Swing listener theo kiểu chờ đọc response. `TcpControlConnection` có reader/writer nền; message tới client được chuyển lên EDT qua `SwingUtilities.invokeLater`. Lệnh gửi được xếp vào hàng đợi và đi ra theo đúng thứ tự.

## 3. File source và trách nhiệm

| File / package | Trách nhiệm chính |
|---|---|
| `StudyFixApplication.java` | Điểm vào chương trình, cài look-and-feel, tải config và mở login. |
| `config/ClientConfig.java` | Host/cổng mặc định, timeout; cho phép override bằng system property. |
| `model/SessionInfo.java` | Danh tính của phiên sau `[LOGIN_OK]`: username, display name, sessionId, userId, role. Không có password trong model. |
| `model/RoomState.java` | View-model trạng thái phòng: roomId, code/version, editor/token, queue, members, draft và history gần nhất. Không có quyền tự grant. |
| `model/MemberInfo.java` | Tên hiển thị và sessionId nếu `/members` cung cấp. Dùng sessionId cho lệnh kick vì server yêu cầu session id, không phải username. |
| `net/FrameCodec.java` | Đọc/ghi protocol TCP: 4 byte signed big-endian length rồi UTF-8 payload, tối đa 65.536 byte. |
| `net/HistoryFrameAssembler.java` | Ghép các frame `[HISTORY_PART i/n]` thành một logical message trước khi parse/UI nhận. |
| `net/TcpControlConnection.java` | Mở một socket lâu dài; reader/writer nền; hàng đợi lệnh; listener; ping định kỳ; quản lý lỗi/ngắt kết nối; matcher phục vụ đăng nhập. |
| `net/ConnectionListener.java` | Hợp đồng callback cho message và disconnect. |
| `protocol/WireCommands.java` | Tạo và validate lệnh server hiện hỗ trợ; tập trung các giới hạn 2.000 ký tự chat, 32.000 byte code, cú pháp lựa chọn dòng. |
| `protocol/ServerMessage.java` | Biểu diễn loại message đã parse nhưng vẫn giữ raw text gốc để không mất phản hồi chưa biết. |
| `protocol/ServerMessageParser.java` | Parse text protocol hiện tại: login, code/version/editor/token/queue, chat, member list, history, draft, screen ticket. Không giả định JSON. |
| `service/AuthService.java` | Kết nối rồi gửi `/login`; chờ `[LOGIN_OK]` hoặc `[LOGIN_FAILED]` có timeout; trả `SessionInfo`. |
| `service/RoomService.java` | Tập trung lệnh tạo/vào/rời phòng, thành viên, kick, chat. |
| `service/CodeCollaborationService.java` | Tập trung thao tác code đúng state machine hiện tại của server. |
| `controller/RoomController.java` | Nhận message, cập nhật `RoomState`, thông báo các panel. Đây là lớp trung gian UI–service. |
| `ui/LoginFrame.java` | Màn hình host/port/username/password; đăng nhập trong `SwingWorker` để không treo EDT. |
| `ui/MainFrame.java` | Cửa sổ chính; tab và trạng thái kết nối; chuyển message lên controller; đóng socket khi thoát. |
| `ui/RoomControlsPanel.java` | Tạo phòng (chỉ hiển thị cho role TEACHER), nhập room id, join/leave. Server vẫn kiểm tra quyền thực tế. |
| `ui/ChatPanel.java` | Chat và các phản hồi liên quan phòng; gửi qua `/chat`. |
| `ui/MembersPanel.java` | Hiển thị thành viên; làm mới bằng `/members`; teacher có thể kick session được chọn. |
| `ui/SharedCodePanel.java` | Hiển thị code/version/editor/queue; xin quyền, chọn dòng, sửa bản nháp, finish/cancel/release, lịch sử và nạp code cho teacher. |
| `ui/HistoryPanel.java` | Gọi `/history` hoặc `/history version`, hiển thị response nguyên văn. |
| `ui/ScreenSharePanel.java` | Giao diện publish/view/stop và xem trước local; stream cần server relay module. |
| `screen/ScreenCaptureService.java` | Chụp desktop bằng `Robot`, resize tối đa 1280px, nén JPEG theo quality 0.62. |
| `screen/ScreenShareWire.java` | Draft binary protocol cho socket màn hình riêng, giới hạn packet 2 MiB. Tách biệt với TCP control UTF-8. |
| `screen/ScreenShareClient.java` | Kết nối publish/view bằng one-time ticket; gửi frame JPEG khoảng 2.5 FPS hoặc nhận ảnh và báo trạng thái. |
| `integration/ClientModule.java` | Giao diện tối thiểu để module bài tập/thi bổ sung tab riêng. |
| `integration/ClientModuleContext.java` | Hợp đồng thành viên 3 dùng được: session, roomState, gửi command, subscribe message/state và báo lỗi. |
| `integration/ClientModuleContextAdapter.java` | Cầu nối context với connection/controller có sẵn; module 3 không tự tạo reader/socket thứ hai. |
| `ui/ExerciseIntegrationPanel.java` | Placeholder trung thực; không giao/nộp/chấm bài giả khi server chưa có API. |
| `ui/ActivityPanel.java` | Log server/client để debug protocol và lỗi. |
| `util/SwingExecutor.java` | Helper chuyển tác vụ về EDT. |
| `src/test/.../ClientSelfTest.java` | Smoke test phụ thuộc bằng 0 cho frame, lịch sử nhiều phần, parser, commands, packet màn hình. |

## 4. Luồng chính phải đúng

### 4.1 Login
1. Người dùng nhập host, port, username, password.
2. `AuthService` tạo socket.
3. `TcpControlConnection` bắt đầu reader/writer và ping nền.
4. Gửi `/login username password`.
5. Chỉ mở MainFrame khi nhận `[LOGIN_OK]`. Nếu `[LOGIN_FAILED]`, timeout hoặc socket lỗi, đóng kết nối và hiển thị lỗi.

### 4.2 Tạo / vào phòng
- Teacher gửi `/create`; server tạo phòng rồi đưa chính phiên đó vào phòng.
- Người học gửi `/join roomId`.
- Một socket điều khiển chỉ gắn với một room hiện tại; muốn đổi phòng phải `/leave` trước.
- Sau join, client đọc snapshot code/state, sự kiện member list và message hệ thống.

### 4.3 Xin quyền sửa code
1. `/request` gửi lên server.
2. Server có thể cấp ngay hoặc thêm vào hàng đợi FIFO.
3. Client parse state `[Phòng id] version=..., editor=..., token=..., hàng đợi=[...]` và hiển thị; không tự coi nút bấm là đã được cấp.
4. Người đang giữ quyền gửi `/select version token 2,5,8` hoặc `/select version token 4-7`.
5. Client gửi từng thao tác bản nháp `/setline token line text`, `/delete token line`, `/keep token line`, `/setrange token text`, `/deleterange token`, `/keeprange token`.
6. `/draft token` xem trước; `/finish token` để server validate/lưu và tăng version; `/cancel token` hủy lượt và nhả quyền. Nếu không có draft, `/release token` trả quyền trực tiếp.
7. Chỉ dùng trạng thái code/version được server broadcast làm kết quả chính thức.

Lưu ý server hiện gửi tên editor chứ chưa gửi `editorSessionId`; trùng display name có thể làm UI khó nhận biết chính xác mình đang giữ quyền. Điều này không làm suy yếu kiểm tra phía server nhưng nên thêm event có session ID khi nhóm ổn định protocol.

### 4.4 Kết thúc phiên / mất mạng
- Reader nhận EOF hoặc lỗi frame thì đóng socket và báo UI.
- Server có heartbeat 20 giây; client gửi `/ping` mỗi 5 giây.
- Nếu disconnect, user cần đăng nhập và join lại; server bắt đầu phiên mới/sessionId mới. Client hiện không tự reconnect để tránh gửi lệnh trên trạng thái cũ.

## 5. Đề xuất kế hoạch làm việc theo mốc

| Mốc | Việc làm | Tiêu chí nghiệm thu |
|---|---|---|
| M1 — nền tảng mạng | FrameCodec, TcpControlConnection, login/heartbeat | Login thành công/thất bại; frame UTF-8; disconnect được báo; không treo UI. |
| M2 — room/chat/member | Create/join/leave, members, chat, room state | Hai client trong cùng phòng nhận chat/member event; khác phòng không nhận chat nhau. |
| M3 — code/version/history | Đọc code, version, history, chunk assembler | Cả hai client thấy cùng code/version sau cập nhật; history dài không vỡ frame. |
| M4 — quyền và bản nháp | Request FIFO, token, selection, line/range, finish/cancel/release | Kiểm thử 2–3 học sinh xin quyền; chỉ editor hiện tại sửa thành công; stale version/token bị server từ chối. |
| M5 — screen-share client + relay | Capture/JPEG, packet v1, relay server, ticket/authorization, UI viewer/publisher | Một người publish mỗi phòng; đúng thành viên mới xem; stop/disconnect dọn session; frame queue có giới hạn. |
| M6 — ghép module người 3 | Context/ClientModule, Exercise/Submission/Contest tabs | Không mở socket/reader trùng; panel gửi nhận event theo protocol được nhóm chốt. |
| M7 — LAN acceptance | Test nhiều máy/Windows firewall/địa chỉ LAN | Một teacher + ít nhất 3 students; kiểm thử reconnect, mất điện, port, encoding tiếng Việt. |

## 6. Tiêu chí không được bỏ qua

- Lệnh nào chưa có trong `ClientHandler` thì không được mô tả là hoạt động.
- Không tự gửi JSON khi server đang dùng command text.
- Không tạo TCP socket mới cho từng lệnh điều khiển.
- Không đọc socket trực tiếp từ Swing EDT.
- Không sử dụng display name như quyền bảo mật; role/token/session do server kiểm tra.
- Không chạy code nộp bài trực tiếp trên client để tự chấm.
- Không gửi màn hình qua socket điều khiển 64 KiB; ảnh dùng relay/socket riêng.
- Không đưa password vào log hoặc lưu ở `SessionInfo`.
## Cập nhật UX sửa code (2026-10-25)

Để giảm thao tác rườm rà, giao diện cộng tác code sử dụng luồng: chọn vùng liên tiếp trong code chung → mở vùng thành bản nháp → sửa trực tiếp trong vùng bản nháp → Xem bản nháp / Hoàn tất lượt sửa / Hủy lượt sửa. Không còn các nút riêng Sửa 1 dòng, Xóa 1 dòng, Giữ 1 dòng, Thay đoạn, Xóa đoạn, Giữ đoạn.

Client gọi `/select version token N-M`, cho phép `N=M` với một dòng; nội dung thay thế được gửi bằng `/setrange token <text>` (vùng trống dùng `/deleterange token`), sau đó `/draft token` để lấy bản xem trước tổng thể. Client yêu cầu xem trước thành công trước khi bật Hoàn tất, và yêu cầu xem trước lại sau mỗi thay đổi tiếp theo. Server vẫn là nguồn quyết định quyền/token/version và lưu code; đây không phải chỉnh sửa code chung cục bộ.

