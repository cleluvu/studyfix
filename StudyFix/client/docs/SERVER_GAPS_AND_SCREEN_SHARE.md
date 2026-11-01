# Gap list server và thiết kế module chia sẻ màn hình

## 1. Gap đã xác minh từ source server hiện tại

### Chưa có trong `ClientHandler` / `Server`

- Không có command `/screen-ticket` hoặc cơ chế cấp ticket màn hình.
- Không có server socket/listener dedicated cho ảnh màn hình.
- Không có quyền `PUBLISH`/`VIEW`, giới hạn một publisher mỗi room hay relay ảnh đến viewers.
- Không có module bài tập, bài nộp, worker chấm, cuộc thi, điểm hoặc bảng xếp hạng.

### Đã có và module màn hình sẽ tận dụng

- `Server.gateway()` trả `StudyFixGateway`.
- `gateway.isMember(sessionId, roomId)` kiểm tra session có thuộc room.
- `gateway.sendToRoom(actorSessionId, roomId, event)` gửi control event tới đúng room và kiểm tra actor membership.
- `gateway.addDepartureListener(...)` / `removeDepartureListener(...)` thông báo khi session rời room/disconnect. Callback chạy trên worker và phải xử lý nhanh.

## 2. Vì sao không dùng sessionId làm ticket

Server `/members` đưa sessionId ra client; do đó không an toàn nếu client chỉ gửi sessionId qua socket ảnh để tự nhận quyền. Module màn hình phải dùng **one-time random ticket** do ClientHandler cấp sau khi lấy session identity hiện có, gắn với sessionId + roomId + mode, hết hạn trong 30 giây và chỉ dùng một lần.

## 3. Thiết kế đề xuất của module trong `server-screen-share-module/`

- `src/studyfix/screenshare/ScreenShareWire.java`: binary packet frame; 4 byte big-endian length, 1 byte type, payload; giới hạn packet 2 MiB. Không dùng text-only `studyfix.Protocol` cho JPEG.
- `src/studyfix/screenshare/ScreenShareRelay.java`: listener riêng, one-time ticket registry, một publisher mỗi room, viewer set theo room, kiểm tra membership qua `StudyFixGateway`, relay JPEG, đóng peer khi session rời phòng.
- `docs/INTEGRATION_PATCH.md`: các bước cụ thể để thêm field/lifecycle vào `Server` và command vào `ClientHandler`. Module chưa được chép vào server gốc và chưa chạy cho đến khi người 1 tích hợp patch.

### Control command được đề xuất để thống nhất

Client sau khi đã join room gửi một trong hai lệnh:

```text
/screen-ticket publish
/screen-ticket view
```

Server xác minh connection đã login và thuộc room hiện tại, sau đó trả đúng format:

```text
[SCREEN_TICKET] mode=PUBLISH room=12 port=5001 ticket=BASE64URL_RANDOM_ONE_TIME_VALUE
```

hoặc `mode=VIEW`. `room` và `port` là giá trị server quyết định, không phải client được tự chọn. Ticket hết hạn sau 30 giây.

### Protocol TCP riêng cho màn hình v1

Mỗi packet: 4 byte signed big-endian length (không tính 4 byte length) + body. Body = byte type + payload. Giới hạn body 2 MiB.

| Type | ID | Payload |
|---|---:|---|
| `HELLO` | 1 | UTF-8 one-time ticket, chỉ packet đầu |
| `FRAME` | 2 | JPEG bytes |
| `STOP` | 3 | empty |
| `ERROR` | 4 | UTF-8 lý do từ chối/lỗi |
| `STATUS` | 5 | UTF-8 trạng thái/ACK |

Publish: client HELLO ticket -> server xác minh -> server nhận FRAME và chuyển cho viewers trong room. View: client HELLO ticket -> server nhận ảnh từ publisher cùng room. Mỗi room chỉ có một publisher. Frame hiện thiết kế cho JPEG tối đa 2 MiB và capture client 1280px, quality khoảng 0.62, 2.5 FPS.

### Yêu cầu xử lý chậm và ngắt kết nối

- Không truyền JPEG trong control socket 64 KiB.
- Mỗi viewer chỉ cần frame mới nhất; hàng đợi gửi ảnh phải giới hạn (khuyến nghị 1 frame), bỏ frame cũ nếu viewer chậm. Không để một viewer chặn publisher.
- Khi publisher stop/disconnect/kick/leave, relay xóa publisher và kết thúc luồng viewer; departure listener phải dọn đúng một lần.
- Khi viewer leave/disconnect, gỡ viewer khỏi room set.
- Giới hạn số peer, frame size, FPS và thời gian handshake.
- Chỉ cấp ticket sau khi kiểm tra room membership; ticket một lần, hết hạn, không ghi ra log đầy đủ.

## 4. Những gì client đang làm được / chưa được

Đã có lớp chụp màn hình local (`Robot`), resize/nén JPEG, panel viewer và codec/client socket cho protocol v1. Nút local preview có thể test capture mà không cần server. Live publish/view hiện chưa hoạt động với baseline server vì server không trả ticket và không mở port 5001. UI không báo “chia sẻ thành công” nếu chưa kết nối được.

## 5. Các thay đổi người 1 cần tích hợp (tóm tắt)

1. Copy hai file trong `server-screen-share-module/src/studyfix/screenshare` vào source server.
2. Khởi tạo `ScreenShareRelay` trong `Server` bằng `Integer.getInteger("studyfix.screen.port", 5001)`; expose getter `screenShare()` và đóng relay trong `Server.close()`.
3. Trong `ClientHandler`, xử lý `/screen-ticket publish|view` sau xác thực và yêu cầu đang ở room; gọi `server.screenShare().issueTicket(getMemberId(), currentRoom.getRoomId(), mode)` rồi trả `[SCREEN_TICKET]...`.
4. Test relay cùng LAN bằng hai viewers; test một publish/room, hết hạn ticket, token dùng lần hai, viewer ngoài room, kick, leave và server shutdown.
5. Thống nhất merge quyền sở hữu file: người 2 maintain module relay, người 1 maintain điểm gắn vào `Server`/`ClientHandler`.
