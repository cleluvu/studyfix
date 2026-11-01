# Giao diện phòng học và cuộc gọi WebRTC

Bản cập nhật ngày 29/10/2026 đưa phòng học Web sang bố cục tối theo hai ảnh tham khảo: giảng viên xem lưới code của sinh viên; sinh viên làm bài trong editor lớn với terminal chấm bài phía dưới. Mic, camera, chia sẻ màn hình, danh sách người tham gia và chat nằm trên thanh điều khiển của phòng học.

## Cách mở

**Cập nhật vòng đời phòng:** nhập mã tự chọn tại Tổng quan; danh sách gồm cả phòng trống, 5 phòng/trang. Phòng được giữ qua restart; chỉ chủ phòng được bấm Xóa cạnh Vào/Mở. Xem [quy tắc hiện hành](ROOM_CODES_AND_LIFECYCLE_VI.md).

Nếu báo `Unable to delete file ...dist\studyfix.jar`, server cũ vẫn đang giữ JAR. Đóng cửa sổ Server Manager cũ hoặc nhấn Ctrl+C trong terminal đã khởi chạy server, chờ dừng hẳn rồi chạy lại. Hai launcher kiểm tra file khóa/cổng bận trước khi build. Launcher dùng `ant -Dbuild.dir=build-launcher jar`, tách khỏi database kiểm thử dưới `build/`, không còn chạy clean xóa cả thư mục test.

Khởi động lại Server Manager để nạp backend mới, rồi mở hoặc tải lại trình duyệt tại `http://localhost:8080`. Nếu server đang chạy, dùng nút dừng/đóng Server Manager trước; không chạy hai server cùng cổng. Chạy từ thư mục `StudyFix`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1
```

Tạo/tham gia phòng tại **Tổng quan**. Màn hình Phòng học nền sáng với hai thẻ tạo/tham gia đã được bỏ theo yêu cầu; khi chưa ở trong phòng, điều hướng Phòng học về Tổng quan. Tạo/tham gia thành công sẽ mở trực tiếp workspace cuộc họp tối. Luồng Master và nhánh riêng vẫn dùng quy trình trong [hướng dẫn phòng học](CLASSROOM_BRANCH_WORKFLOW_VI.md).

## Rời cuộc họp, rời phòng và tải lại trang

- Nút đỏ trong workspace là **Rời cuộc họp**: dừng mic/camera, đóng luồng chia sẻ/nhận màn hình, báo rời cuộc gọi và về Tổng quan. Giữ nguyên phòng đang tham gia, tài khoản và dữ liệu phòng. Không gửi `LEAVE_ROOM`.
- Trong **Phòng học của hệ thống**, phòng đang tham gia có **Vào phòng** và **Rời phòng** cạnh nhau. Vào phòng mở lại cuộc họp; Rời phòng có xác nhận, gửi `LEAVE_ROOM`, bỏ tham gia và cho phép vào phòng khác. Chỉ phòng đang tham gia mới có nút Rời phòng; quyền Xóa vẫn chỉ thuộc chủ phòng.
- Sidebar màn hình chính không tự thêm mục Phòng học theo trạng thái thành viên. Vào cuộc họp từ danh sách phòng.
- Ctrl+Shift+R khi đang họp: sau khi kiểm tra phiên và phòng trên server, mở lại đúng workspace. Không tự bật lại mic/camera. Sau Rời cuộc họp hoặc Rời phòng, tải lại vẫn ở Tổng quan.
- Ghi nhớ màn hình theo tab, tài khoản và ID phòng; không khôi phục vào phòng đã xóa hoặc tài khoản khác. Phản hồi đồng bộ phòng cũ bị bỏ khi đang rời phòng; tín hiệu màn hình không tự mở lại media sau Rời cuộc họp.

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

### Tin nhắn chưa đọc

Khi có tin mới từ người khác, ứng dụng hiện thẻ thông báo Tin nhắn mới ở góc trên, có nút Mở chat, kể cả khi khung Chat đang mở. Nếu chưa xem, nút Chat có huy hiệu đỏ đếm tin và tiêu đề tab hiển thị số chưa đọc. Tin do chính tài khoản gửi và thông báo vận hành không được tính.

Chỉ đánh dấu đã đọc khi cửa sổ/tab có focus, đang ở Phòng học, khung Chat mở và đã cuộn tới cuối. Tab ẩn, đang xem tin cũ ở phía trên hoặc đang mở hộp thoại không tự xóa bộ đếm. Cập nhật tin mới giữ nguyên vị trí cuộn nếu người dùng đang đọc lịch sử.

Mốc đã đọc lưu riêng theo tài khoản và phòng trong localStorage của trình duyệt, được giữ khi tải lại trang; không phải biên nhận đã đọc cho người gửi và chưa đồng bộ mốc đọc giữa các máy/trình duyệt khác nhau. Lần đầu tham gia trên trình duyệt này thiết lập mốc ở lịch sử hiện tại, tránh thông báo lại toàn bộ tin cũ.

Backend đếm trên toàn bộ lịch sử, dù giao diện chỉ tải 80 tin gần nhất; định danh người gửi dùng userId để không nhầm các tài khoản trùng tên hiển thị. Khởi động lại Server Manager để nạp endpoint chat mới rồi Ctrl+Shift+R ở các máy. Trước khi restart, frontend vẫn có đường tương thích để tiếp tục đọc chat từ backend cũ, với số đếm giới hạn theo phần lịch sử cũ trả về.

Test thủ công: giảng viên đóng Chat, sinh viên gửi hai tin; giảng viên phải thấy số 2 và thông báo. Mở Chat và xem cuối danh sách thì số biến mất. Lặp lại khi đang ở Bài tập, khi tab bị ẩn và khi giảng viên cuộn lên đọc tin cũ. Kiểm tra gửi tin từ chính tài khoản không làm tăng số chưa đọc. Đây là thông báo trong ứng dụng/tab, không yêu cầu quyền thông báo hệ điều hành.

**Sửa lỗi media một chiều ngày 30/10/2026:** phía trả lời SDP trước đây tạo transceiver riêng trước khi nhận offer, khiến sender mic/camera có thể không thuộc các kênh đã được thương lượng. Bản sửa dùng transceiver được tạo từ remote offer, đặt sendrecv và gắn track/stream trước khi tạo answer. Chỉ phía tạo offer chuẩn bị kênh từ đầu. Đồng thời các lần polling từ nút thiết bị và timer được xếp tuần tự để không xử lý offer trùng. Quy tắc ghép transceiver được đối chiếu với [chuẩn WebRTC](https://www.w3.org/TR/webrtc/).

Sau cập nhật, cả giảng viên và sinh viên nhấn Ngắt cuộc gọi, tải lại trang bằng Ctrl+Shift+R, vào lại cùng phòng và thử cả hai chiều. Đổi thứ tự tham gia/bật thiết bị để kiểm tra cả phía tạo offer và phía trả lời. Đây là thay đổi frontend; server hiện phục vụ file JS mới, không cần build Java cho bản sửa này.

1. Nhấn **Tham gia nghe** nếu chỉ muốn nhận âm thanh, không yêu cầu quyền thiết bị.
2. Nhấn **Bật mic** để cho phép microphone và gửi âm thanh; nhấn lại để mute/unmute.
3. Nhấn **Bật camera** để cấp quyền camera; tắt camera sẽ dừng track camera và gỡ khỏi sender.
4. **Ngắt cuộc gọi** dừng tất cả track và đóng kết nối media nhưng vẫn ở trong phòng học.
5. **Rời cuộc họp** dừng cuộc gọi/chia sẻ màn hình và về Tổng quan, vẫn tham gia phòng. Muốn bỏ tham gia, chọn **Rời phòng** cạnh Vào phòng trong danh sách. Đăng xuất cũng dọn media. Đóng tab gửi thông báo rời gọi; nếu tab mất kết nối đột ngột, server loại khỏi danh sách gọi sau khoảng 20 giây không polling.

Khi chuyển sang Bài tập/Tổng quan trong lúc đang gọi, thanh cuộc gọi nổi vẫn cho biết thiết bị đang hoạt động và cho phép tắt mic/camera/ngắt gọi. Âm thanh nhận được tiếp tục phát. Nếu trình duyệt chặn autoplay, nhấn **Bật âm thanh nhận** trong phòng.

Camera, mic và screen share cần secure context. `localhost` có thể dùng HTTP để phát triển; từ thiết bị khác qua IP LAN cần HTTPS với chứng chỉ được trình duyệt tin cậy, theo cấu hình TLS trong `RUN_IN_VSCODE_VI.md`. Không cần camera/mic để làm bài hoặc chat.

## Phạm vi kết nối

### Chia sẻ YouTube có âm thanh

Từ bản sửa ngày 30/10/2026, screen share yêu cầu cả video và audio; trước đó `audio: false` khiến người nhận chỉ có hình ảnh. Trình duyệt vẫn quyết định nguồn âm thanh nào khả dụng và người dùng phải chủ động chọn chia sẻ âm thanh.

1. Dừng screen share cũ. Hai máy tải lại trang bằng Ctrl+Shift+R để nạp mã mới.
2. Mở YouTube trong một tab riêng trên Chrome/Edge desktop.
3. Trong StudyFix nhấn Chia sẻ, chọn nhóm Tab trình duyệt rồi chọn tab YouTube.
4. Bật tùy chọn Chia sẻ âm thanh tab / Share tab audio trong hộp thoại, sau đó xác nhận chia sẻ và phát video.
5. Máy nhận mở Màn hình. Nếu trình duyệt chặn autoplay, nhấn Phát âm thanh chia sẻ.

Giao diện báo có luồng âm thanh hay chỉ có hình ảnh. Nếu chỉ có hình, dừng và chọn nguồn lại với tùy chọn âm thanh. Chia sẻ cửa sổ/toàn màn hình không đảm bảo có âm thanh trên mọi trình duyệt/hệ điều hành, kể cả khi ứng dụng đã yêu cầu `systemAudio: include`. Xem [hướng dẫn của Chrome](https://developer.chrome.com/docs/web-platform/screen-sharing-controls).

Âm thanh tab được gửi qua kết nối screen share riêng, không yêu cầu bật microphone và không bị tắt cùng nút mute mic. Preview tại máy phát được mute để tránh phát âm thanh hai lần; ứng dụng yêu cầu giữ playback tại tab nguồn bằng `suppressLocalAudioPlayback: false`. Nút dừng chia sẻ dừng cả track hình và âm thanh. Chưa nghiệm thu YouTube audio trên hai trình duyệt thật; test capture/RTC/DOM mô phỏng có 13 kiểm tra PASS.

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

Kiểm thử riêng cuộc gọi: **33 checks HTTP/signaling PASS**; frontend: **29 kiểm tra hành vi workspace** và **29 kiểm tra media mô phỏng PASS**. Mock transceiver đã mô hình hóa quy tắc ghép kênh của SDP: test gửi mic từ phía trả lời thất bại trên mã cũ và qua trên bản sửa. Có kiểm tra camera bật sau answer, stream identity, không tạo kênh dư và không xử lý offer hai lần khi polling đồng thời. Các kiểm tra khác xác minh ICE tới sớm, thay track, mute/unmute, từ chối quyền và dọn capture khi rời phòng. Chúng không chứng minh truyền hình/âm thanh thật.

Chưa có kiểm thử hình ảnh giao diện và camera/audio giữa hai trình duyệt thật vì browser access host hiện không kết nối được. Cần nghiệm thu trên ít nhất hai thiết bị: mic hai chiều, camera hai chiều, bật/tắt camera khi đang nói, chia sẻ màn hình trong khi gọi, từ chối quyền thiết bị, mất mạng/vào lại, và rời phòng. Bộ test dùng database tạm dưới `server/build/`; không chạy migration trên database người dùng để kiểm thử.
