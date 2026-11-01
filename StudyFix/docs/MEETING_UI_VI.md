# Giao diện phòng học và cuộc gọi WebRTC

Bản cập nhật ngày 29/10/2026 đưa phòng học Web sang bố cục tối theo hai ảnh tham khảo: giảng viên xem lưới code của sinh viên; sinh viên làm bài trong editor lớn với terminal chấm bài phía dưới. Mic, camera, chia sẻ màn hình, danh sách người tham gia và chat nằm trên thanh điều khiển của phòng học.

## Cách mở

Nếu báo `Unable to delete file ...dist\studyfix.jar`, server cũ vẫn đang giữ JAR. Đóng cửa sổ Server Manager cũ hoặc nhấn Ctrl+C trong terminal đã khởi chạy server, chờ dừng hẳn rồi chạy lại. Không xóa JAR/database và không dừng mọi tiến trình Java trên máy. Hai launcher kiểm tra file khóa/cổng bận trước khi chạy Ant clean để thông báo tình huống này sớm.

Khởi động lại Server Manager để nạp backend mới, rồi mở hoặc tải lại trình duyệt tại `http://localhost:8080`. Nếu server đang chạy, dùng nút dừng/đóng Server Manager trước; không chạy hai server cùng cổng. Chạy từ thư mục `StudyFix`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1
```

Vào mục Phòng học và tạo/tham gia cùng một phòng. Luồng Master và nhánh riêng vẫn dùng quy trình trong [hướng dẫn phòng học](CLASSROOM_BRANCH_WORKFLOW_VI.md).

## Giao diện giảng viên

- **Lưới bài làm** hiển thị tối đa 25 ô mỗi trang. Mỗi ô có tên thật của tài khoản, trạng thái online, phiên bản nhánh, kết quả chấm và 14 dòng code xem trước từ backend. Có bộ lọc tên, trực tuyến và cần hỗ trợ. Sinh viên không nhận được bản xem trước của người khác.
- **Master** mở editor của giảng viên. Việc chỉnh sửa và lưu vẫn chỉ được phép trong giai đoạn giảng bài.
- Tách nhánh, dừng sửa, cho sửa tiếp và kết thúc phiên nằm trên thanh tác vụ. Chọn một ô để mở code đầy đủ và lịch sử kiểm tra; Merge dùng đúng phiên bản đang xem và chỉ xuất hiện trong giai đoạn chọn lời giải.
- Các camera đang bật xuất hiện thành dải video phía trên nội dung. Avatar được dùng khi chưa có luồng video; không hiển thị video hoặc trạng thái đang nói giả.

## Giao diện sinh viên

- Bên trái là giảng viên và các thành viên thực tế trong phòng. Camera bật và đã nhận được luồng sẽ thay avatar.
- **Nhánh của tôi** là vùng làm bài riêng. **Master** cho xem bản code chung. Editor có số dòng, hỗ trợ Tab thụt lề và giữ vị trí đang nhập khi cập nhật trạng thái.
- **Challenge Terminal** hiển thị feedback/verdict từ lượt chấm thật. Không tạo log lỗi hoặc kết quả Passed giả. Kết quả cũ được đánh dấu nếu nhánh đã đổi phiên bản.
- Lưu tự động, tải code, khôi phục nháp, kiểm tra code và lịch sử các vòng trước vẫn được giữ.

## Mic và camera

1. Nhấn **Tham gia nghe** nếu chỉ muốn nhận âm thanh, không yêu cầu quyền thiết bị.
2. Nhấn **Bật mic** để cho phép microphone và gửi âm thanh; nhấn lại để mute/unmute.
3. Nhấn **Bật camera** để cấp quyền camera; tắt camera sẽ dừng track camera và gỡ khỏi sender.
4. **Ngắt cuộc gọi** dừng tất cả track và đóng kết nối media nhưng vẫn ở trong phòng học.
5. **Rời phòng** dừng cuộc gọi, dừng screen share và rời phòng học. Đăng xuất cũng dọn media. Đóng tab gửi thông báo rời gọi; nếu tab mất kết nối đột ngột, server loại khỏi danh sách gọi sau khoảng 20 giây không polling.

Khi chuyển sang Bài tập/Tổng quan trong lúc đang gọi, thanh cuộc gọi nổi vẫn cho biết thiết bị đang hoạt động và cho phép tắt mic/camera/ngắt gọi. Âm thanh nhận được tiếp tục phát. Nếu trình duyệt chặn autoplay, nhấn **Bật âm thanh nhận** trong phòng.

Camera, mic và screen share cần secure context. `localhost` có thể dùng HTTP để phát triển; từ thiết bị khác qua IP LAN cần HTTPS với chứng chỉ được trình duyệt tin cậy, theo cấu hình TLS trong `RUN_IN_VSCODE_VI.md`. Không cần camera/mic để làm bài hoặc chat.

## Phạm vi kết nối

Cuộc gọi dùng WebRTC mesh trực tiếp giữa các thiết bị cùng LAN, giới hạn **16 thiết bị tham gia media mỗi phòng**. Các sinh viên khác vẫn dùng code và chat. Chưa có SFU/TURN; không đảm bảo hoạt động qua NAT, các mạng khác nhau hoặc Wi-Fi chặn liên lạc giữa thiết bị. Chưa có đo tải 16 camera thật. Muốn mở rộng thành dịch vụ hội nghị cho lớp đông hoặc qua Internet cần bổ sung hạ tầng media và nghiệm thu tải riêng.

Audio/video của cuộc gọi và screen share dùng peer connections/streams riêng. Tắt camera không tắt chia sẻ màn hình. Relay Swing vẫn tách biệt; bản này không tạo cầu nối audio/video với Swing.

## API và kiểm thử

`POST /api/call/signal` nhận `JOIN`, `STATE`, `LEAVE`, `OFFER`, `ANSWER`, `ICE`. `GET /api/call/signals?after=N` trả roster và tín hiệu dành cho phiên hiện tại. Cả hai yêu cầu bearer token và thành viên phòng. Sender/room được lấy từ phiên đăng nhập; người gửi và người nhận phải cùng phòng và đã chủ động tham gia cuộc gọi.

Signaling giới hạn 600 gói/phút/người gửi, 512 gói và 256.000 ký tự payload đang chờ/người nhận; ICE tối đa 4.096 ký tự, SDP tối đa 64.000 ký tự. Không lưu media hoặc tín hiệu cuộc gọi vào database. Endpoint hiện hữu giữ nguyên quyền sửa Master/nhánh. Permissions Policy chỉ cho camera/microphone của chính origin ứng dụng.

```powershell
# Từ server
ant meeting-test
ant integration-test

# Từ web-client
node tests/check_classroom.mjs
node tests/check_meeting.mjs
node tests/check_imports.mjs
node tests/check_seven_bugfixes.mjs
```

Kiểm thử riêng cuộc gọi: **33 checks HTTP/signaling PASS**; frontend: **29 kiểm tra hành vi workspace** và **23 kiểm tra media mô phỏng PASS**. Các kiểm tra media dùng RTCPeerConnection, MediaStream và thiết bị giả lập để xác minh offer/answer, ICE tới sớm, thay track, mute/unmute, từ chối quyền và dọn capture khi rời phòng. Chúng không chứng minh truyền hình/âm thanh thật.

Chưa có kiểm thử hình ảnh giao diện và camera/audio giữa hai trình duyệt thật vì browser access host hiện không kết nối được. Cần nghiệm thu trên ít nhất hai thiết bị: mic hai chiều, camera hai chiều, bật/tắt camera khi đang nói, chia sẻ màn hình trong khi gọi, từ chối quyền thiết bị, mất mạng/vào lại, và rời phòng. Bộ test dùng database tạm dưới `server/build/`; không chạy migration trên database người dùng để kiểm thử.
